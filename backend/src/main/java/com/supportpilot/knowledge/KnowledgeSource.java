package com.supportpilot.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Remembers which knowledge-base files are embedded, so unchanged files are not re-embedded. */
@Entity
@Table(name = "knowledge_sources")
public class KnowledgeSource {

    @Id
    @Column(length = 255)
    private String source;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt;

    protected KnowledgeSource() {}

    public KnowledgeSource(String source, String contentHash, int chunkCount, Instant loadedAt) {
        this.source = source;
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.loadedAt = loadedAt;
    }

    public String getSource() {
        return source;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }
}
