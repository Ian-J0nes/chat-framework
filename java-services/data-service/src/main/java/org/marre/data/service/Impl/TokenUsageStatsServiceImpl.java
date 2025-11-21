package org.marre.data.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.marre.common.entity.TokenUsageStats;
import org.marre.data.mapper.TokenUsageStatsMapper;
import org.marre.data.service.TokenUsageStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TokenUsageStatsServiceImpl implements TokenUsageStatsService {

    @Autowired
    TokenUsageStatsMapper tokenUsageStatsMapper;

    @Override
    public void updateTokenUsageStats(Long userId, String model, LocalDate date, Long promptTokens, Long completionTokens) {
        TokenUsageStats stats = getStatsByUserModelDate(userId, model, date);

        if(stats==null){
            stats = new TokenUsageStats();
            stats.setUserId(userId);
            stats.setModel(model);
            stats.setDate(date);
            stats.setPromptTokens(promptTokens);
            stats.setCompletionTokens(completionTokens);
            stats.setTotalTokens(promptTokens + completionTokens);
            stats.setCreateTime(LocalDateTime.now());
            stats.setUpdateTime(LocalDateTime.now());
            tokenUsageStatsMapper.insert(stats);
        }else {
            stats.setTotalRequests(stats.getTotalRequests() + 1);
            stats.setPromptTokens(stats.getPromptTokens() + promptTokens);
            stats.setCompletionTokens(stats.getCompletionTokens() + completionTokens);
            stats.setTotalTokens(stats.getTotalTokens() + promptTokens + completionTokens);
            stats.setUpdateTime(LocalDateTime.now());
            tokenUsageStatsMapper.updateById(stats);
        }
    }

    // TODO
    @Override
    public TokenUsageStats getOrCreateStats(Long userId, String model, LocalDate date) {
        QueryWrapper <TokenUsageStats> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("model",model);
        queryWrapper.eq("date",date);
        TokenUsageStats stats = tokenUsageStatsMapper.selectOne(queryWrapper);
        if(stats==null){
            stats = new TokenUsageStats();
            stats.setUserId(userId);
            stats.setModel(model);
            stats.setDate(date);
            stats.setCreateTime(LocalDateTime.now());
            stats.setUpdateTime(LocalDateTime.now());
        }
        return stats;
    }

    @Override
    public TokenUsageStats getStatsByUserModelDate(Long userId, String model, LocalDate date) {
        QueryWrapper<TokenUsageStats> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("model",model);
        queryWrapper.eq("date",date);
        TokenUsageStats tokenUsageStats = tokenUsageStatsMapper.selectOne(queryWrapper);
        return tokenUsageStats;
    }

    @Override
    public List<TokenUsageStats> getUserStatsInDateRange(Long userId, LocalDate start, LocalDate end) {
        return List.of();
    }

    @Override
    public List<TokenUsageStats> getUserStatsByModel(Long userId, String model) {
        return List.of();
    }

    @Override
    public Map<String, Long> getTotalTokenByUser(Long userId) {
        return Map.of();
    }
}
