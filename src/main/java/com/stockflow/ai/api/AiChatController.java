package com.stockflow.ai.api;

import com.stockflow.ai.dto.AiChatRequest;
import com.stockflow.ai.dto.AiChatResponse;
import com.stockflow.ai.service.GeminiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final GeminiChatService chatService;

    public AiChatController(GeminiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new AiChatResponse("Vui lòng nhập câu hỏi để được trợ lý AI tư vấn.", false));
        }
        return ResponseEntity.ok(chatService.chat(request.getMessage().trim()));
    }
}
