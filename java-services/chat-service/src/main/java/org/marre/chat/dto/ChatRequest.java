package org.marre.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    private List<ChatMessage> messages;
    @JsonProperty("user_id")
    private Long userId;
    private String model;
    private String sessionId;

    /**
     * 幂等请求ID（可选）。
     * 入参：前端建议使用 UUID 赋值为 request_id；若为空，服务端会临时生成，仅当次有效。
     * 出参：不参与返回，但会用于写入用户消息的 requestId 字段。
     */
    @JsonProperty("request_id")
    private String requestId;
}
