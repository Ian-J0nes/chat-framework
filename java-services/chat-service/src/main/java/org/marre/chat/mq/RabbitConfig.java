package org.marre.chat.mq;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑配置（KISS）：
 * - DirectExchange: chat.x
 * - Queues: chat.generate（主队列）、chat.generate.retry（延迟重试，TTL 回流）、chat.generate.dlq（死信）
 * - Bindings: routing key 精确路由到对应队列
 */
@Configuration
public class RabbitConfig {

    @Value("${app.mq.exchange:chat.x}")
    private String exchangeName;

    @Value("${app.mq.routing.generate:chat.generate}")
    private String routingGenerate;

    @Value("${app.mq.routing.retry:chat.generate.retry}")
    private String routingRetry;

    @Value("${app.mq.routing.dlq:chat.generate.dlq}")
    private String routingDlq;

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue chatGenerateQueue() {
        return QueueBuilder.durable(routingGenerate)
                .build();
    }

    @Bean
    public Queue chatGenerateRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        // 10 秒后投回主队列
        args.put("x-message-ttl", 10_000);
        args.put("x-dead-letter-exchange", exchangeName);
        args.put("x-dead-letter-routing-key", routingGenerate);
        return new Queue(routingRetry, true, false, false, args);
    }

    @Bean
    public Queue chatGenerateDlq() {
        return QueueBuilder.durable(routingDlq).build();
    }

    @Bean
    public Binding bindGenerate(DirectExchange chatExchange, @Qualifier("chatGenerateQueue") Queue chatGenerateQueue) {
        return BindingBuilder.bind(chatGenerateQueue).to(chatExchange).with(routingGenerate);
    }

    @Bean
    public Binding bindRetry(DirectExchange chatExchange, @Qualifier("chatGenerateRetryQueue") Queue chatGenerateRetryQueue) {
        return BindingBuilder.bind(chatGenerateRetryQueue).to(chatExchange).with(routingRetry);
    }

    @Bean
    public Binding bindDlq(DirectExchange chatExchange, @Qualifier("chatGenerateDlq") Queue chatGenerateDlq) {
        return BindingBuilder.bind(chatGenerateDlq).to(chatExchange).with(routingDlq);
    }
}
