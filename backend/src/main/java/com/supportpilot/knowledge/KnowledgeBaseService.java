package com.supportpilot.knowledge;

import com.supportpilot.config.SupportPilotProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Loads the Markdown knowledge base into the vector store at startup.
 *
 * <p>Each file's SHA-256 is stored, so restarts only re-embed files that changed. If the
 * embedding provider is not reachable (for example Ollama is not running), the error is
 * logged and reported by {@code /api/info} instead of stopping the application. Loading can be
 * retried with {@code POST /api/admin/knowledge/reload}.
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final int MAX_SECTION_CHARS = 1500;

    private final VectorStore vectorStore;
    private final KnowledgeSourceRepository sources;
    private final ResourcePatternResolver resolver;
    private final SupportPilotProperties.Knowledge config;
    private final Clock clock;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter(MAX_SECTION_CHARS);
    private final AtomicReference<Status> status = new AtomicReference<>(new Status("NOT_LOADED", 0, 0, null));

    public record Status(String state, int files, int chunks, String error) {}

    public KnowledgeBaseService(VectorStore vectorStore, KnowledgeSourceRepository sources,
                                ResourcePatternResolver resolver, SupportPilotProperties properties,
                                Clock clock) {
        this.vectorStore = vectorStore;
        this.sources = sources;
        this.resolver = resolver;
        this.config = properties.knowledge();
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (config.loadOnStartup()) {
            load();
        }
    }

    public synchronized Status load() {
        try {
            Resource[] files = resolver.getResources(config.location());
            Arrays.sort(files, Comparator.comparing(Resource::getFilename));
            int embedded = 0;
            int totalChunks = 0;
            for (Resource file : files) {
                String name = file.getFilename();
                String content = file.getContentAsString(StandardCharsets.UTF_8);
                String hash = sha256(content);
                KnowledgeSource existing = sources.findById(name).orElse(null);
                if (existing != null && existing.getContentHash().equals(hash)) {
                    totalChunks += existing.getChunkCount();
                    continue;
                }
                List<Document> documents = new ArrayList<>(splitter.split(name, content));
                if (existing != null) {
                    vectorStore.delete(new FilterExpressionBuilder().eq("source", name).build());
                }
                vectorStore.add(documents);
                sources.save(new KnowledgeSource(name, hash, documents.size(), Instant.now(clock)));
                totalChunks += documents.size();
                embedded++;
                log.info("Embedded knowledge file {} ({} chunks)", name, documents.size());
            }
            Status ready = new Status("READY", files.length, totalChunks, null);
            status.set(ready);
            log.info("Knowledge base ready: {} files, {} chunks ({} re-embedded)", files.length,
                    totalChunks, embedded);
            return ready;
        } catch (IOException | RuntimeException e) {
            Status failed = new Status("FAILED", 0, 0, e.getMessage());
            status.set(failed);
            log.warn("Could not load the knowledge base: {}. The chat still works but without "
                    + "policy context until it is reloaded.", e.getMessage());
            return failed;
        }
    }

    public Status status() {
        return status.get();
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
