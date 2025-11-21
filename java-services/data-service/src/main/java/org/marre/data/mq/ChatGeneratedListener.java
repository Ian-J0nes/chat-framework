package org.marre.data.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.marre.common.entity.ChatMessage;
import org.marre.data.service.ChatMessageService;
import org.marre.data.service.TokenUsageStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消费 chat.generated：将助手消息持久化（幂等），并更新用量统计。
 */
@Component
public class ChatGeneratedListener {
    private static final Logger log = LoggerFactory.getLogger(ChatGeneratedListener.class);

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private TokenUsageStatsService tokenUsageStatsService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${app.mq.exchange:chat.x}")
    private String exchangeName;

    @Value("${app.mq.routing.generatedRetry:chat.generated.retry}")
    private String routingGeneratedRetry;

    @Value("${app.mq.routing.generatedDlq:chat.generated.dlq}")
    private String routingGeneratedDlq;

    private final ObjectMapper mapper = new ObjectMapper();

    @RabbitListener(queues = "#{chatGeneratedQueue.name}")
    public void onGenerated(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode root = mapper.readTree(body);
            String sessionId = root.path("session_id").asText();
            long userId = root.path("user_id").asLong();
            String model = root.path("model").asText();
            String response = root.path("response").asText();
            String requestId = root.path("request_id").asText();

            // 幂等：为助手生成一个派生的 requestId，避免与用户消息冲突
            String aiRid = (requestId == null || requestId.isEmpty()) ? null : (requestId + ":assistant");

            ChatMessage ai = new ChatMessage();
            ai.setSessionId(sessionId);
            ai.setUserId(userId);
            ai.setRole("assistant");
            ai.setContent(response);
            ai.setModel(model);
            ai.setRequestId(aiRid);
            ai.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
            ai.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
            ai.setTotalTokens(root.path("usage").path("total_tokens").asInt(0));
            ai.setCreateTime(LocalDateTime.now());
            ai.setDeleted(0);

            boolean inserted = chatMessageService.saveMessageIfAbsent(ai);

            // 仅在新插入时累计用量，避免重复消费导致的重复累计
            if (inserted) {
                try {
                    tokenUsageStatsService.updateTokenUsageStats(
                            userId,
                            model,
                            LocalDate.now(),
                            (long) ai.getPromptTokens(),
                            (long) ai.getCompletionTokens()
                    );
                } catch (Exception e) {
                    log.warn("update token usage failed: {}", e.getMessage());
                }
            } else {
                log.debug("assistant message already exists, skip token usage accumulation: sessionId={}, requestId={}", sessionId, aiRid);
            }

        } catch (Exception ex) {
            log.warn("persist generated failed, will route to retry: {}", ex.getMessage());
            // 简单重试：转发到 retry，超过 5 次转 DLQ
            try {
                Integer retry = (Integer) message.getMessageProperties().getHeaders().getOrDefault("x-retry-count", 0);
                int next = retry == null ? 1 : retry + 1;
                message.getMessageProperties().setHeader("x-retry-count", next);
                if (next <= 5) {
                    rabbitTemplate.send(exchangeName, routingGeneratedRetry, message);
                } else {
                    rabbitTemplate.send(exchangeName, routingGeneratedDlq, message);
                }
            } catch (Exception e) {
                log.error("publish retry/dlq failed: {}", e.getMessage());
            }
        }
    }
}
