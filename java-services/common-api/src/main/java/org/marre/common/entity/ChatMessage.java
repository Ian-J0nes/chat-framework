package org.marre.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_messages")

public class ChatMessage {

    private Long id;

    private String sessionId;

    private Long userId;

    private String role;

    private String content;

    private String model;

    /**
     * 幂等请求ID（同一 sessionId 下相同 requestId 的消息只保存一次）
     * 入参：由客户端或上游服务生成并透传
     * 出参：原样回写
     */
    private String requestId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Integer deleted;

}
