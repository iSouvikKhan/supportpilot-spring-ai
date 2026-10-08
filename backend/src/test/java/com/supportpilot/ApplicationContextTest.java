package com.supportpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supportpilot.order.OrderRepository;
import com.supportpilot.refund.RefundService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole application with the default provider settings and no API keys. H2 replaces
 * PostgreSQL and the vector store is mocked (see application-test.yml); no Ollama server is
 * contacted because model beans only connect when used.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationContextTest {

    @MockitoBean
    VectorStore vectorStore;

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mvc;

    @Autowired
    OrderRepository orders;

    @Test
    void startsWithOllamaOnlyAndNoApiKeys() {
        Map<String, ChatModel> chatModels = context.getBeansOfType(ChatModel.class);
        assertThat(chatModels.values()).singleElement().isInstanceOf(OllamaChatModel.class);
        assertThat(context.getBeansOfType(EmbeddingModel.class).values())
                .singleElement().isInstanceOf(OllamaEmbeddingModel.class);
        assertThat(context.getBean(ChatClient.class)).isNotNull();
        assertThat(context.getBean(RefundService.class)).isNotNull();
    }

    @Test
    void sampleOrdersAreSeeded() throws Exception {
        assertThat(orders.count()).isEqualTo(8);
        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/orders").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ORD-1002"))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/orders/ORD-1004").param("email", "alice@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void infoReportsProviders() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatProvider").value("ollama"))
                .andExpect(jsonPath("$.chatModel").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"));
    }
}
