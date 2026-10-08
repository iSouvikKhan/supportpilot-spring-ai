package com.supportpilot.agent;

import com.supportpilot.common.AiUnavailableException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String SYSTEM_PROMPT = """
            You are SupportPilot, the customer-support assistant of ByteBay Electronics, an online \
            electronics store.

            The signed-in customer is {customerName} ({customerEmail}). Today is {today}.

            How to help:
            - Answer questions about policies (shipping, returns, refunds, warranty, payments, \
            accounts) using ONLY the context documents provided with the question. If they do not \
            cover the question, say so and offer to connect the customer with a human agent. \
            Never invent policies, prices or dates.
            - For anything about a specific order, call the order tools. Never guess order data.
            - Refunds: you cannot issue refunds yourself. When a customer wants one, make sure you \
            know the order id and the reason, then call requestRefund. Tell the customer the \
            request is PENDING human review. Never say a refund is approved unless a tool result \
            says APPROVED.
            - If a tool reports a problem (for example outside the refund window), explain it \
            politely and suggest what the customer can do instead.
            - Be concise, friendly and specific. Use short paragraphs or bullet points.""";

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public record Source(String source, String title, String excerpt) {}

    public record Reply(String text, List<AgentAction> actions, List<Source> sources) {}

    public Reply chat(String conversationId, String customerEmail, String customerName, String message,
                      String today) {
        AgentActions actions = new AgentActions();
        // Memory is keyed per customer so conversations never leak between customers.
        String memoryKey = customerEmail.toLowerCase() + ":" + conversationId;

        ChatClientResponse response;
        try {
            response = chatClient.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT)
                            .param("customerName", customerName)
                            .param("customerEmail", customerEmail)
                            .param("today", today))
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                    .toolContext(Map.of(
                            SupportTools.CUSTOMER_EMAIL, customerEmail,
                            SupportTools.CONVERSATION_ID, conversationId,
                            SupportTools.ACTIONS, actions))
                    .call()
                    .chatClientResponse();
        } catch (RuntimeException e) {
            log.error("AI provider call failed", e);
            throw new AiUnavailableException(
                    "The AI provider is unavailable: " + rootMessage(e)
                            + ". Check that it is running and configured (see README).", e);
        }

        ChatResponse chatResponse = response.chatResponse();
        String text = chatResponse == null || chatResponse.getResult() == null
                ? ""
                : chatResponse.getResult().getOutput().getText();
        return new Reply(text == null ? "" : text.strip(), actions.list(), sources(response));
    }

    @SuppressWarnings("unchecked")
    static List<Source> sources(ChatClientResponse response) {
        Object docs = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(docs instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Document>) list).stream()
                .map(d -> new Source(
                        String.valueOf(d.getMetadata().getOrDefault("source", "knowledge base")),
                        String.valueOf(d.getMetadata().getOrDefault("title", "")),
                        excerpt(d.getText())))
                .distinct()
                .toList();
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 240 ? flat : flat.substring(0, 240).replaceAll("\\s\\S*$", "") + "…";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
