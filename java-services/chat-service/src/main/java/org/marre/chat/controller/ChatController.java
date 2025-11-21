package org.marre.chat.controller;

import lombok.extern.slf4j.Slf4j;
import org.marre.chat.client.DataServiceClient;
import org.marre.chat.client.LLMServiceClient;
import org.marre.chat.mq.ChatTaskPublisher;
import org.marre.chat.dto.ChatRequest;
import org.marre.chat.dto.ChatResponse;
import org.marre.common.entity.ChatMessage;
import org.marre.common.entity.ChatSession;
import org.marre.common.entity.TokenUsageStats;
import org.marre.common.exception.BusinessException;
import org.marre.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final LLMServiceClient llmServiceClient;
    private final DataServiceClient dataServiceClient;
    private final ChatTaskPublisher chatTaskPublisher;
    private final int contextMaxMessages;

    public ChatController(
            LLMServiceClient llmServiceClient,
            DataServiceClient dataServiceClient,
            ChatTaskPublisher chatTaskPublisher,
            @Value("${app.context.maxMessages:12}") int contextMaxMessages) {
        this.llmServiceClient = llmServiceClient;
        this.dataServiceClient = dataServiceClient;
        this.chatTaskPublisher = chatTaskPublisher;
        this.contextMaxMessages = contextMaxMessages;
    }

    @RequestMapping("send")
    public ResponseEntity<Result<?>> send(@RequestBody ChatRequest req) {
        // 参数校验
        if (req.getSessionId() == null || req.getSessionId().isEmpty()) {
            throw new BusinessException(400, "sessionId 不能为空");
        }
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new BusinessException(400, "messages 不能为空");
        }

        // 1) Ensure session exists (by sessionId)
        ensureSessionExists(req);

        // 2) Save user message
        saveUserMessage(req);

        // 3) Build context history (recent N) for LLM
        List<ChatMessage> all = fetchSessionMessages(req.getSessionId());
        List<Map<String, Object>> history = buildHistory(all);

        // 4) Publish MQ task (async) and return 202 Accepted
        Map<String, Object> payload = new HashMap<>();
        payload.put("request_id", req.getRequestId());
        payload.put("session_id", req.getSessionId());
        payload.put("user_id", req.getUserId());
        payload.put("model", req.getModel());
        String userContent = req.getMessages().get(req.getMessages().size() - 1).getContent();
        payload.put("last_user_message", userContent);
        if (!history.isEmpty()) {
            payload.put("history", history);
        }
        log.info("发送聊天请求: sessionId={}, model={}", req.getSessionId(), req.getModel());
        chatTaskPublisher.sendGenerate(payload);

        Map<String, Object> accepted = new HashMap<>();
        accepted.put("request_id", req.getRequestId());
        accepted.put("sessionId", req.getSessionId());
        return new ResponseEntity<>(Result.accepted(accepted), HttpStatus.ACCEPTED);
    }

    /**
     * 获取会话历史消息
     */
    private List<ChatMessage> fetchSessionMessages(String sessionId) {
        try {
            Result<List<ChatMessage>> res = dataServiceClient.getSessionMessages(sessionId);
            return (res != null && res.getData() != null) ? res.getData() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("获取会话历史失败: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建上下文历史
     */
    private List<Map<String, Object>> buildHistory(List<ChatMessage> all) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (all == null || all.isEmpty()) {
            return history;
        }
        int from = Math.max(0, all.size() - Math.max(1, contextMaxMessages));
        for (int i = from; i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (m == null || m.getRole() == null || m.getContent() == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            history.add(item);
        }
        return history;
    }

    /**
     * Ensure the session exists by sessionId; create if not found.
     *
     * @param req ChatRequest input (sessionId, userId, model)
     */
    private void ensureSessionExists(ChatRequest req) {
        try {
            Result<ChatSession> result = dataServiceClient.getSessionBySessionId(req.getSessionId());
            if (result != null && result.getData() != null) {
                return; // found
            }
        } catch (Exception e) {
            log.debug("查询会话失败，将创建新会话: sessionId={}", req.getSessionId());
        }

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(req.getSessionId());
        chatSession.setUserId(req.getUserId());
        chatSession.setModel(req.getModel());
        chatSession.setTitle("会话");
        chatSession.setCreateTime(LocalDateTime.now());
        chatSession.setUpdateTime(LocalDateTime.now());
        chatSession.setDeleted(0);

        dataServiceClient.createSession(chatSession);
    }

    /**
     * Save the user's last message of this request.
     *
     * @param req ChatRequest input
     */
    private void saveUserMessage(ChatRequest req) {
        String userContent = req.getMessages().get(req.getMessages().size() - 1).getContent();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(req.getSessionId());
        userMessage.setUserId(req.getUserId());
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        userMessage.setModel(req.getModel());
        // 幂等ID：前端传入 request_id，否则这里生成一次性ID（仅当次重试无法保证）
        String rid = req.getRequestId() != null && !req.getRequestId().isEmpty() ? req.getRequestId() : UUID.randomUUID().toString();
        userMessage.setRequestId(rid);
        userMessage.setCreateTime(LocalDateTime.now());
        userMessage.setDeleted(0);

        dataServiceClient.saveMessage(userMessage);
    }

    /**
     * Save assistant message with token usage if provided.
     *
     * @param req ChatRequest input
     * @param chatResponse LLM reply
     */
    private void saveAIMessage(ChatRequest req, ChatResponse chatResponse) {
        ChatMessage aiMessage = new ChatMessage();
        aiMessage.setSessionId(req.getSessionId());
        aiMessage.setUserId(req.getUserId());
        aiMessage.setModel(req.getModel());
        aiMessage.setRole("assistant");
        aiMessage.setContent(chatResponse.getResponse());

        if (chatResponse.getUsage() != null) {
            aiMessage.setPromptTokens(chatResponse.getUsage().getPromptTokens());
            aiMessage.setCompletionTokens(chatResponse.getUsage().getCompletionTokens());
            aiMessage.setTotalTokens(chatResponse.getUsage().getTotalTokens());
        } else {
            aiMessage.setPromptTokens(0);
            aiMessage.setCompletionTokens(0);
            aiMessage.setTotalTokens(0);
        }

        aiMessage.setCreateTime(LocalDateTime.now());
        aiMessage.setDeleted(0);

        dataServiceClient.saveMessage(aiMessage);
    }

    /**
     * Update token usage daily stats if usage provided in response.
     *
     * @param req ChatRequest input
     * @param chatResponse LLM reply
     */
    private void updateTokenStats(ChatRequest req, ChatResponse chatResponse) {
        if (chatResponse.getUsage() != null) {
            TokenUsageStats tokenStats = new TokenUsageStats();
            tokenStats.setUserId(req.getUserId());
            tokenStats.setModel(req.getModel());
            tokenStats.setDate(LocalDate.now());
            tokenStats.setPromptTokens((long) chatResponse.getUsage().getPromptTokens());
            tokenStats.setCompletionTokens((long) chatResponse.getUsage().getCompletionTokens());
            tokenStats.setTotalTokens((long) chatResponse.getUsage().getTotalTokens());

            dataServiceClient.updateTokenStats(tokenStats);
        }
    }
}
