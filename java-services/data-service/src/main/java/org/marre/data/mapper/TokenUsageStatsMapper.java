package org.marre.data.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.marre.common.entity.TokenUsageStats;
@Mapper
@TableName("token_usage_stats")
public interface TokenUsageStatsMapper extends BaseMapper<TokenUsageStats> {
}
