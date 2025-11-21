package org.marre.chat.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MQ 生产者：发布 chat.generate 任务。
 * 入参：构建好的任务负载（见 sendGenerate）。
 * 出参：无（发布至 RabbitMQ）。
 */
@Service
public class ChatTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.mq.exchange:chat.x}")
    private String exchangeName;

    @Value("${app.mq.routing.generate:chat.generate}")
    private String routingGenerate;

    public ChatTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送“生成回复”任务。
     * @param payload 任务内容（必须包含 request_id, session_id, user_id, model, last_user_message）
     */
    public void sendGenerate(Map<String, Object> payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            MessageProperties mp = new MessageProperties();
            mp.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            mp.setContentEncoding(StandardCharsets.UTF_8.name());
            mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            if (payload.get("request_id") != null) {
                mp.setMessageId(String.valueOf(payload.get("request_id")));
            }
            mp.setType("chat.generate.v1");
            mp.setTimestamp(java.util.Date.from(Instant.now()));
            // 可选头部冗余
            if (payload.get("session_id") != null) mp.setHeader("x-session-id", payload.get("session_id"));
            if (payload.get("user_id") != null) mp.setHeader("x-user-id", payload.get("user_id"));
            if (payload.get("model") != null) mp.setHeader("x-model", payload.get("model"));

            Message message = new Message(body, mp);
            rabbitTemplate.send(exchangeName, routingGenerate, message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("encode mq payload failed: " + e.getMessage(), e);
        }
    }
}

