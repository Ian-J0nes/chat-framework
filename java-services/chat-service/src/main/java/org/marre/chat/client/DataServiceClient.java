package org.marre.chat.client;

import org.marre.common.entity.ChatMessage;
import org.marre.common.entity.ChatSession;
import org.marre.common.entity.TokenUsageStats;
import org.marre.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "data-service")
public interface DataServiceClient {

    // 会话相关
    @PostMapping("/api/data/sessions")
    Result<ChatSession> createSession(@RequestBody ChatSession session);

    /**
     * 按业务 sessionId 查询会话
     * 入参：sessionId（路径）
     * 出参：Result<ChatSession>
     */
    @GetMapping("/api/data/sessions/by-session-id/{sessionId}")
    Result<ChatSession> getSessionBySessionId(@PathVariable("sessionId") String sessionId);

    // 消息相关
    @PostMapping("/api/data/messages")
    Result<ChatMessage> saveMessage(@RequestBody ChatMessage message);

    @GetMapping("/api/data/sessions/{sessionId}/messages")
    Result<List<ChatMessage>> getSessionMessages(@PathVariable("sessionId") String sessionId);

    // Token统计相关
    @PostMapping("/api/data/stats/tokens")
    Result updateTokenStats(@RequestBody TokenUsageStats tokenStats);

}
