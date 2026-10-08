package com.supportpilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supportpilot.common.AiUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

class ChatServiceTest {

    /** A ChatModel stub that records prompts and returns a canned answer. */
    static class StubChatModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        final String answer;

        StubChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }

    @Test
    void sendsSystemPromptWithCustomerAndReturnsReply() {
        StubChatModel model = new StubChatModel("  Your order ORD-1001 was delivered.  ");
        ChatService service = new ChatService(ChatClient.builder(model).build());

        ChatService.Reply reply = service.chat("conv-12345", "alice@example.com", "Alice", "Where is my order?",
                "2026-03-15");

        assertThat(reply.text()).isEqualTo("Your order ORD-1001 was delivered.");
        assertThat(reply.actions()).isEmpty();
        String system = model.prompts.getFirst().getSystemMessage().getText();
        assertThat(system).contains("Alice (alice@example.com)").contains("Today is 2026-03-15")
                .contains("cannot issue refunds yourself");
        assertThat(model.prompts.getFirst().getUserMessage().getText()).isEqualTo("Where is my order?");
    }

    @Test
    void providerErrorsBecomeAiUnavailable() {
        ChatModel failing = prompt -> {
            throw new IllegalStateException("wrap", new java.net.ConnectException("Connection refused"));
        };
        ChatService service = new ChatService(ChatClient.builder(failing).build());

        assertThatThrownBy(() -> service.chat("conv-12345", "a@b.co", "A", "hi", "2026-03-15"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void extractsDistinctSourcesFromRetrievedDocuments() {
        Document d1 = new Document("Refund window text that is long enough", Map.of("source", "returns.md", "title", "Refunds - Window"));
        Document d2 = new Document("Shipping text", Map.of("source", "shipping.md", "title", "Shipping"));
        ChatClientResponse response = new ChatClientResponse(null,
                Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of(d1, d2, d1)));

        List<ChatService.Source> sources = ChatService.sources(response);

        assertThat(sources).extracting(ChatService.Source::source).containsExactly("returns.md", "shipping.md");
        assertThat(sources.getFirst().title()).isEqualTo("Refunds - Window");
    }

    @Test
    void noRetrievedDocumentsMeansNoSources() {
        assertThat(ChatService.sources(new ChatClientResponse(null, Map.of()))).isEmpty();
    }
}
