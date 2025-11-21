package org.marre.data.mq;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-service 侧声明“生成结果”相关队列：
 * - chat.generated（主队列）
 * - chat.generated.retry（延迟重试，TTL 后 DLX 回流）
 * - chat.generated.dlq（死信）
 */
@Configuration
public class RabbitConfig {

    @Value("${app.mq.exchange:chat.x}")
    private String exchangeName;

    @Value("${app.mq.routing.generated:chat.generated}")
    private String routingGenerated;

    @Value("${app.mq.routing.generatedRetry:chat.generated.retry}")
    private String routingGeneratedRetry;

    @Value("${app.mq.routing.generatedDlq:chat.generated.dlq}")
    private String routingGeneratedDlq;

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue chatGeneratedQueue() {
        return QueueBuilder.durable(routingGenerated).build();
    }

    @Bean
    public Queue chatGeneratedRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 10_000);
        args.put("x-dead-letter-exchange", exchangeName);
        args.put("x-dead-letter-routing-key", routingGenerated);
        return new Queue(routingGeneratedRetry, true, false, false, args);
    }

    @Bean
    public Queue chatGeneratedDlq() {
        return QueueBuilder.durable(routingGeneratedDlq).build();
    }

    @Bean
    public Binding bindGenerated(DirectExchange chatExchange, @Qualifier("chatGeneratedQueue") Queue chatGeneratedQueue) {
        return BindingBuilder.bind(chatGeneratedQueue).to(chatExchange).with(routingGenerated);
    }

    @Bean
    public Binding bindGeneratedRetry(DirectExchange chatExchange, @Qualifier("chatGeneratedRetryQueue") Queue chatGeneratedRetryQueue) {
        return BindingBuilder.bind(chatGeneratedRetryQueue).to(chatExchange).with(routingGeneratedRetry);
    }

    @Bean
    public Binding bindGeneratedDlq(DirectExchange chatExchange, @Qualifier("chatGeneratedDlq") Queue chatGeneratedDlq) {
        return BindingBuilder.bind(chatGeneratedDlq).to(chatExchange).with(routingGeneratedDlq);
    }
}
