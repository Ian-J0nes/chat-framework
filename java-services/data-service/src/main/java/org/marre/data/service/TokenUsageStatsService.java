package org.marre.data.service;

import org.marre.common.entity.TokenUsageStats;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public interface TokenUsageStatsService {

    void updateTokenUsageStats(Long userId, String model, LocalDate date, Long promptTokens, Long completionTokens);

    TokenUsageStats getOrCreateStats(Long userId, String model, LocalDate date);

    TokenUsageStats getStatsByUserModelDate(Long userId, String model, LocalDate date);

    List<TokenUsageStats> getUserStatsInDateRange(Long userId, LocalDate start, LocalDate end);

    List<TokenUsageStats> getUserStatsByModel(Long userId, String model);

    Map<String, Long> getTotalTokenByUser(Long userId);

}
