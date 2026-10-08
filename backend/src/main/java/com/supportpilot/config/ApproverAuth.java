package com.supportpilot.config;

import com.supportpilot.common.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guards approver-only endpoints with a shared key sent in the {@code X-Approver-Key} header.
 *
 * <p>If {@code APPROVER_API_KEY} is not configured, a random key is generated at startup and
 * printed to the log (similar to Spring Security's generated password), so no secret has to be
 * committed for local development.
 */
@Component
public class ApproverAuth {

    public static final String HEADER = "X-Approver-Key";
    private static final Logger log = LoggerFactory.getLogger(ApproverAuth.class);

    private final byte[] key;

    public ApproverAuth(SupportPilotProperties properties) {
        String configured = properties.approver().apiKey();
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[18];
            new SecureRandom().nextBytes(random);
            configured = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            log.warn("APPROVER_API_KEY is not set. Generated approver key for this run: {}", configured);
        }
        this.key = configured.getBytes(StandardCharsets.UTF_8);
    }

    public void require(String providedKey) {
        if (providedKey == null
                || !MessageDigest.isEqual(key, providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("A valid " + HEADER + " header is required");
        }
    }
}
