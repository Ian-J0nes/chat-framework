package org.marre.data.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.ChatMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public interface ChatMessageService {

    ChatMessage saveMessage(ChatMessage chatMessage);

    /**
     * 幂等保存消息（尝试插入，若命中联合唯一约束则视为已存在）
     * 入参：ChatMessage（要求提供 sessionId + requestId 以启用幂等）
     * 反参：boolean（true=新插入，false=已存在/未插入）
     */
    boolean saveMessageIfAbsent(ChatMessage chatMessage);

    List<ChatMessage> getMessagesBySessionId(String sessionId);

    ChatMessage getMessageById(Long id);

    boolean deleteMessageById(Long id);

    Page<ChatMessage> getSessionMessagePage(String sessionId, int page, int size);

    List<ChatMessage> getRecentMessage(String sessionId, int limit);

    long getMessageCount(String sessionId);

    List<ChatMessage> getMessageByUserAndTimeRange(Long userId, LocalDateTime start, LocalDateTime end);


}
