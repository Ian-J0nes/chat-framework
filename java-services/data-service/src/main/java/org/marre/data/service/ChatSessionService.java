package org.marre.data.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.ChatSession;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ChatSessionService {

    ChatSession createSession(ChatSession session);

    ChatSession getSessionById(Long id);

    ChatSession getSessionBySessionId(String sessionId);

    List<ChatSession> getSessionByUserId(Long userId);

    ChatSession updateSession(ChatSession session);

    boolean deleteSession(String sessionId);

    Page<ChatSession> getUserSessionPage(Long userid, int page, int size);

    boolean existsSession(String sessionId);

    List<ChatSession> getRecentSessions(Long userId, int limit);

}
