package com.supportpilot.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings bound from {@code supportpilot.*} (see application.yml). */
@Validated
@ConfigurationProperties(prefix = "supportpilot")
public record SupportPilotProperties(
        Approver approver, Rag rag, Refunds refunds, Knowledge knowledge, List<String> corsOrigins) {

    public SupportPilotProperties {
        approver = approver == null ? new Approver(null) : approver;
        rag = rag == null ? new Rag(4, 0.4) : rag;
        refunds = refunds == null ? new Refunds(30) : refunds;
        knowledge = knowledge == null ? new Knowledge("classpath:knowledge/*.md", true) : knowledge;
        corsOrigins = corsOrigins == null ? List.of("http://localhost:4200") : corsOrigins;
    }

    /** Shared key the approver console sends in the X-Approver-Key header. Generated if blank. */
    public record Approver(String apiKey) {}

    public record Rag(
            @Min(1) @Max(20) int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold) {}

    public record Refunds(@Min(1) @Max(365) int windowDays) {}

    public record Knowledge(@NotBlank String location, boolean loadOnStartup) {}
}
