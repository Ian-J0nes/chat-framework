package org.marre.chat.client;

import org.marre.chat.dto.ChatRequest;
import org.marre.chat.dto.ChatResponse;
import org.marre.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name = "llm-service", url = "${app.llm.service.url:http://localhost:18080}")
public interface LLMServiceClient {

    @RequestMapping("/api/llm/chat")
    Result<ChatResponse> chat(@RequestBody ChatRequest req);
}
