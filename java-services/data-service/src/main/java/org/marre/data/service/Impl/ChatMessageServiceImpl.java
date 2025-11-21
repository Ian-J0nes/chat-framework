package org.marre.data.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.ChatMessage;
import org.marre.data.mapper.ChatMessageMapper;
import org.marre.data.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Autowired
    ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessage saveMessage(ChatMessage chatMessage) {
        // 幂等性：若携带 requestId，则在同一 sessionId 下相同 requestId 只插入一次
        if (chatMessage.getRequestId() != null && chatMessage.getSessionId() != null) {
            ChatMessage existing = chatMessageMapper.selectOne(new QueryWrapper<ChatMessage>()
                    .eq("session_id", chatMessage.getSessionId())
                    .eq("request_id", chatMessage.getRequestId()));
            if (existing != null) {
                return existing;
            }
        }
        chatMessageMapper.insert(chatMessage);
        return chatMessage;
    }

    @Override
    public boolean saveMessageIfAbsent(ChatMessage chatMessage) {
        // 要求：提供 sessionId + requestId 才能依赖联合唯一约束实现幂等
        try {
            chatMessageMapper.insert(chatMessage);
            return true; // 新插入
        } catch (DataIntegrityViolationException e) {
            // 命中唯一约束，视为已存在（非新插入）
            return false;
        }
    }

    @Override
    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        QueryWrapper <ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        queryWrapper.orderByAsc("create_time"); // 时间正序
        List<ChatMessage> chatMessages = chatMessageMapper.selectList(queryWrapper);
        return chatMessages;
    }

    @Override
    public ChatMessage getMessageById(Long id) {
        return chatMessageMapper.selectById(id);
    }

    @Override
    public boolean deleteMessageById(Long id) {
        return chatMessageMapper.deleteById(id) > 0;
    }

    @Override
    public Page<ChatMessage> getSessionMessagePage(String sessionId, int page, int size) {
       Page<ChatMessage> pageList = new Page<>(page, size);
       QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
       queryWrapper.eq("session_id",sessionId);
       return chatMessageMapper.selectPage(pageList,queryWrapper);
    }

    @Override
    public List<ChatMessage> getRecentMessage(String sessionId, int limit) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        queryWrapper.orderByAsc("create_time");
        queryWrapper.last("LIMIT "+limit);
        return chatMessageMapper.selectList(queryWrapper);
    }

    @Override
    public long getMessageCount(String sessionId) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id",sessionId);
        return chatMessageMapper.selectCount(queryWrapper);
    }

    @Override
    public List<ChatMessage> getMessageByUserAndTimeRange(Long userId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.between("create_time",start,end);
        return chatMessageMapper.selectList(queryWrapper);
    }
}
