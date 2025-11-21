package org.marre.data.mq;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 RabbitListener（监听器）。
 */
@Configuration
@EnableRabbit
public class RabbitListenerConfig {
}

