package com.supportpilot.config;

import com.supportpilot.agent.SupportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The agent's ChatClient. The underlying ChatModel is whichever provider
 * {@code spring.ai.model.chat} selects (Ollama by default), so switching providers is
 * configuration only.
 */
@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore,
                          SupportTools supportTools, SupportPilotProperties properties) {
        SearchRequest search = SearchRequest.builder()
                .topK(properties.rag().topK())
                .similarityThreshold(properties.rag().similarityThreshold())
                .build();
        return builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).searchRequest(search).build(),
                        new SimpleLoggerAdvisor())
                .defaultTools(supportTools)
                .build();
    }
}
