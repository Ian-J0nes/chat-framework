package org.marre.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("token_usage_stats")

public class TokenUsageStats {

    private Long id;

    private Long userId;

    private String model;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private Integer totalRequests;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
