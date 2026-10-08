package com.supportpilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    static final String CONVERSATION_ID_PATTERN = "^[A-Za-z0-9-]{8,64}$";

    private final ChatService chatService;
    private final ChatMemory chatMemory;
    private final Clock clock;

    public ChatController(ChatService chatService, ChatMemory chatMemory, Clock clock) {
        this.chatService = chatService;
        this.chatMemory = chatMemory;
        this.clock = clock;
    }

    public record ChatRequest(
            @NotBlank @Pattern(regexp = CONVERSATION_ID_PATTERN) String conversationId,
            @NotBlank @Email String customerEmail,
            @NotBlank @Size(max = 100) String customerName,
            @NotBlank @Size(max = 2000) String message) {}

    public record ChatResponse(
            String conversationId,
            String reply,
            List<AgentAction> actions,
            List<ChatService.Source> sources) {}

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        ChatService.Reply reply = chatService.chat(
                request.conversationId(),
                request.customerEmail().strip(),
                request.customerName().strip(),
                request.message().strip(),
                LocalDate.now(clock).toString());
        return new ChatResponse(request.conversationId(), reply.text(), reply.actions(), reply.sources());
    }

    /** Forget a conversation's memory (the "New conversation" button). */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> reset(
            @PathVariable @Pattern(regexp = CONVERSATION_ID_PATTERN) String conversationId,
            @RequestParam @Email @NotBlank String email) {
        chatMemory.clear(email.strip().toLowerCase() + ":" + conversationId);
        return ResponseEntity.noContent().build();
    }
}
