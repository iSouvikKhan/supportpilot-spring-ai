package com.supportpilot.config;

import com.supportpilot.knowledge.KnowledgeBaseService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {

    private final Environment env;
    private final KnowledgeBaseService knowledgeBase;
    private final ApproverAuth approverAuth;

    public InfoController(Environment env, KnowledgeBaseService knowledgeBase, ApproverAuth approverAuth) {
        this.env = env;
        this.knowledgeBase = knowledgeBase;
        this.approverAuth = approverAuth;
    }

    public record Info(String chatProvider, String chatModel, String embeddingProvider,
                       String embeddingModel, KnowledgeBaseService.Status knowledgeBase) {}

    /** Active providers/models and knowledge-base state, shown in the UI header. */
    @GetMapping("/api/info")
    public Info info() {
        String chat = env.getProperty("spring.ai.model.chat", "ollama");
        String embedding = env.getProperty("spring.ai.model.embedding", "ollama");
        return new Info(chat, chatModel(chat), embedding, embeddingModel(embedding), knowledgeBase.status());
    }

    /** Re-run knowledge-base loading, e.g. after starting Ollama. Approver key required. */
    @PostMapping("/api/admin/knowledge/reload")
    public KnowledgeBaseService.Status reload(
            @RequestHeader(value = ApproverAuth.HEADER, required = false) String key) {
        approverAuth.require(key);
        return knowledgeBase.load();
    }

    private String chatModel(String provider) {
        return switch (provider) {
            case "openai" -> env.getProperty("spring.ai.openai.chat.options.model", "");
            case "google-genai" -> env.getProperty("spring.ai.google.genai.chat.options.model", "");
            default -> env.getProperty("spring.ai.ollama.chat.options.model", "");
        };
    }

    private String embeddingModel(String provider) {
        return switch (provider) {
            case "openai" -> env.getProperty("spring.ai.openai.embedding.options.model", "");
            default -> env.getProperty("spring.ai.ollama.embedding.options.model", "");
        };
    }
}
