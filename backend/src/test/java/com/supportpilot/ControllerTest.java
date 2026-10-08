package com.supportpilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supportpilot.agent.AgentAction;
import com.supportpilot.agent.ChatController;
import com.supportpilot.agent.ChatService;
import com.supportpilot.common.AiUnavailableException;
import com.supportpilot.common.BusinessRuleException;
import com.supportpilot.config.ApproverAuth;
import com.supportpilot.config.SupportPilotProperties;
import com.supportpilot.order.OrderController;
import com.supportpilot.order.OrderService;
import com.supportpilot.refund.RefundController;
import com.supportpilot.refund.RefundRequest;
import com.supportpilot.refund.RefundService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {RefundController.class, ChatController.class, OrderController.class})
@Import({ApproverAuth.class, ControllerTest.Config.class})
class ControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        SupportPilotProperties properties() {
            return TestData.properties();
        }

        @Bean
        Clock clock() {
            return TestData.CLOCK;
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RefundService refundService;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    ChatMemory chatMemory;

    @MockitoBean
    OrderService orderService;

    private static final String DECISION = "{\"approverName\":\"Dana\",\"note\":\"ok\"}";

    @Test
    void approverEndpointsRequireKey() throws Exception {
        mvc.perform(get("/api/refunds")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid X-Approver-Key header is required"));
        mvc.perform(get("/api/refunds").header(ApproverAuth.HEADER, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/refunds/" + UUID.randomUUID() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON).content(DECISION))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approveWithValidKey() throws Exception {
        RefundRequest r = RefundRequest.pending("ORD-1", "alice@example.com", BigDecimal.TEN, "r", null, TestData.NOW);
        when(refundService.approve(eq(r.getId()), eq("Dana"), eq("ok"))).thenReturn(r);

        mvc.perform(post("/api/refunds/" + r.getId() + "/approve").header(ApproverAuth.HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(DECISION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-1"));
    }

    @Test
    void decisionValidationAndConflicts() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/refunds/" + id + "/reject").header(ApproverAuth.HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approverName\":\"\"}"))
                .andExpect(status().isBadRequest());

        when(refundService.reject(eq(id), anyString(), any())).thenThrow(new BusinessRuleException("already APPROVED"));
        mvc.perform(post("/api/refunds/" + id + "/reject").header(ApproverAuth.HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(DECISION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("already APPROVED"));

        mvc.perform(post("/api/refunds/not-a-uuid/reject").header(ApproverAuth.HEADER, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(DECISION))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatReturnsReplyActionsAndSources() throws Exception {
        when(chatService.chat(eq("conv-12345678"), eq("alice@example.com"), eq("Alice"), eq("Refund ORD-1001"), anyString()))
                .thenReturn(new ChatService.Reply("Submitted for review.",
                        List.of(new AgentAction(AgentAction.Type.REFUND_REQUESTED, "ORD-1001", UUID.randomUUID(),
                                "PENDING", new BigDecimal("129.99"), "Refund request submitted")),
                        List.of(new ChatService.Source("returns-and-refunds.md", "Refunds", "..."))));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                        {"conversationId":"conv-12345678","customerEmail":"alice@example.com",
                         "customerName":"Alice","message":"Refund ORD-1001"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Submitted for review."))
                .andExpect(jsonPath("$.actions[0].type").value("REFUND_REQUESTED"))
                .andExpect(jsonPath("$.actions[0].status").value("PENDING"))
                .andExpect(jsonPath("$.sources[0].source").value("returns-and-refunds.md"));
    }

    @Test
    void chatValidatesInput() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                        {"conversationId":"x","customerEmail":"not-an-email","customerName":"","message":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatReportsUnavailableProvider() throws Exception {
        when(chatService.chat(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AiUnavailableException("The AI provider is unavailable: Connection refused", null));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                        {"conversationId":"conv-12345678","customerEmail":"a@b.co","customerName":"A","message":"hi"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The AI provider is unavailable: Connection refused"));
    }

    @Test
    void ordersRequireValidEmail() throws Exception {
        mvc.perform(get("/api/orders").param("email", "nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders")).andExpect(status().isBadRequest());
    }
}
