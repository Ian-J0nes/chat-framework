package org.marre.data.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.ChatSession;
import org.marre.data.mapper.ChatSessionMapper;
import org.marre.data.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    @Autowired
    ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSession createSession(ChatSession session) {
        chatSessionMapper.insert(session);
        return session;
    }

    @Override
    public ChatSession getSessionById(Long id) {
        return chatSessionMapper.selectById(id);
    }

    @Override
    public ChatSession getSessionBySessionId(String  sessionId) {
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        return chatSessionMapper.selectOne(queryWrapper);
    }

    @Override
    public List<ChatSession> getSessionByUserId(Long userId) {
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.orderByDesc("update_time"); // 按更新时间倒序
        return chatSessionMapper.selectList(queryWrapper);
    }

    @Override
    public ChatSession updateSession(ChatSession session) {
        chatSessionMapper.updateById(session);
        return session;
    }

    @Override
    public boolean deleteSession(String sessionId) {

        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        return chatSessionMapper.delete(queryWrapper) > 0;
    }

    @Override
    public Page<ChatSession> getUserSessionPage(Long userid, int page, int size) {
        Page<ChatSession> pageList = new Page<>(page, size);
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userid);
        return chatSessionMapper.selectPage(pageList,queryWrapper);
    }

    @Override
    public boolean existsSession(String sessionId) {
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        return chatSessionMapper.exists(queryWrapper);
    }

    @Override
    public List<ChatSession> getRecentSessions(Long userId, int limit) {
        QueryWrapper<ChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.orderByDesc("update_time");
        queryWrapper.last("LIMIT "+limit);
        return chatSessionMapper.selectList(queryWrapper);
    }
}
