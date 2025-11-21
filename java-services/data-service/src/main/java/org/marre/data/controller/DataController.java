package org.marre.data.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.ChatMessage;
import org.marre.common.entity.ChatSession;
import org.marre.common.entity.TokenUsageStats;
import org.marre.common.result.Result;
import org.marre.data.service.ChatMessageService;
import org.marre.data.service.ChatSessionService;
import org.marre.data.service.TokenUsageStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final TokenUsageStatsService tokenUsageStatsService;

    public DataController(
            ChatSessionService chatSessionService,
            ChatMessageService chatMessageService,
            TokenUsageStatsService tokenUsageStatsService) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.tokenUsageStatsService = tokenUsageStatsService;
    }

    /**
     * 创建会话
     *
     * @param session 会话实体（入参：包含 sessionId、userId、model 等字段）
     * @return Result<ChatSession>（出参：创建后的会话）
     */
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody ChatSession session){
        chatSessionService.createSession(session);
        return Result.success(session);
    }

    /**
     * 通过数据库主键 ID 获取会话
     *
     * @param id 会话主键ID
     * @return Result<ChatSession>
     */
    @GetMapping("/sessions/{id}")
    public Result<ChatSession> getSessionById(@PathVariable("id") Long id){
        ChatSession session = chatSessionService.getSessionById(id);
        return Result.success(session);
    }

    /**
     * 通过业务 sessionId 获取会话
     *
     * 说明：为与 chat-service 的按 sessionId 查询保持一致，新增该端点。
     *
     * @param sessionId 业务会话ID
     * @return Result<ChatSession>
     */
    @GetMapping("/sessions/by-session-id/{sessionId}")
    public Result<ChatSession> getSessionBySessionId(@PathVariable("sessionId") String sessionId){
        ChatSession session = chatSessionService.getSessionBySessionId(sessionId);
        return Result.success(session);
    }

    @GetMapping("/users/{userId}/sessions")
    public Result<List<ChatSession>> getSessionsByUserId(@PathVariable("userId") Long userId){
        List<ChatSession> list = chatSessionService.getSessionByUserId(userId);
        return Result.success(list);
    }

    /**
     * 删除会话（按业务 sessionId）
     *
     * @param sessionId 业务会话ID
     * @return Result<Boolean> 是否删除成功
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result deleteSession(@PathVariable("sessionId") String sessionId){
        boolean result = chatSessionService.deleteSession(sessionId);
        return Result.success(result);
    }

    // Message相关接口
    @PostMapping("/messages")
    public Result<ChatMessage> saveMessage(@RequestBody ChatMessage chatMessage){
        chatMessageService.saveMessage(chatMessage);
        return Result.success(chatMessage);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable("sessionId") String sessionId){
        List<ChatMessage> list = chatMessageService.getMessagesBySessionId(sessionId);
        return Result.success(list);
    }

    @GetMapping("/sessions/{sessionId}/messages/page")
    public Result<Page<ChatMessage>> getMessagesPage(@PathVariable("sessionId") String sessionId,
                                                     @RequestParam int page,
                                                     @RequestParam int size){
        Page<ChatMessage> pageResult = chatMessageService.getSessionMessagePage(sessionId, page, size);
        return Result.success(pageResult);
    }

    @GetMapping("/sessions/{sessionId}/messages/count")
    public Result<Long> getMessagesCount(@PathVariable("sessionId") String sessionId){
        return Result.success(chatMessageService.getMessageCount(sessionId));
    }

    // TokenUsage相关接口
    @PostMapping("/stats/tokens")
    public Result updateTokenUsageStats(@RequestBody TokenUsageStats tokenUsageStats){
        Long userId = tokenUsageStats.getUserId();
        String model = tokenUsageStats.getModel();
        LocalDate date = LocalDate.now();
        Long promptTokens = tokenUsageStats.getPromptTokens();
        Long completionTokens = tokenUsageStats.getCompletionTokens();
        tokenUsageStatsService.updateTokenUsageStats(userId, model, date, promptTokens, completionTokens);
        return Result.success();
    }

    @GetMapping("/users/{userId}/status")
    public Result<TokenUsageStats> getTokenUsageStats(@PathVariable("userId") Long userId, @RequestParam String model, @RequestParam String date){
        LocalDate localDate = LocalDate.parse(date);
        TokenUsageStats stats = tokenUsageStatsService.getOrCreateStats(userId, model, localDate);
        return Result.success(stats);
    }

}
