package com.supportpilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supportpilot.TestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class KnowledgeBaseTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
    private final KnowledgeBaseService service = new KnowledgeBaseService(vectorStore, sources,
            new PathMatchingResourcePatternResolver(), TestData.properties(), TestData.CLOCK);

    @Test
    void splitterCreatesOneDocumentPerSectionWithTitleAndStableIds() {
        String md = "# Refund Policy\n\nIntro text.\n\n## Window\n\n30 days.\n\n## Timing\n\n5 to 10 days.\n";
        MarkdownSectionSplitter splitter = new MarkdownSectionSplitter(500);

        List<Document> docs = splitter.split("refunds.md", md);

        assertThat(docs).hasSize(3);
        assertThat(docs.get(1).getText()).startsWith("Refund Policy - Window").contains("30 days.");
        assertThat(docs.get(1).getMetadata()).containsEntry("source", "refunds.md")
                .containsEntry("title", "Refund Policy - Window");
        assertThat(splitter.split("refunds.md", md).get(1).getId()).isEqualTo(docs.get(1).getId());
    }

    @Test
    void splitterBreaksLongSectionsOnParagraphs() {
        String para = "word ".repeat(60).strip();
        String md = "# T\n\n## Long\n\n" + para + "\n\n" + para + "\n\n" + para;
        List<Document> docs = new MarkdownSectionSplitter(700).split("x.md", md);
        assertThat(docs).hasSizeGreaterThan(1);
        assertThat(docs).allSatisfy(d -> assertThat(d.getText().length()).isLessThan(800));
    }

    @Test
    void bundledKnowledgeBaseIsEmbeddedOnFirstLoad() {
        when(sources.findById(any())).thenReturn(Optional.empty());

        KnowledgeBaseService.Status status = service.load();

        assertThat(status.state()).isEqualTo("READY");
        assertThat(status.files()).isEqualTo(4);
        assertThat(status.chunks()).isGreaterThan(10);
        verify(vectorStore, times(4)).add(anyList());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void unchangedFilesAreSkippedAndChangedFilesReplaced() throws Exception {
        String shippingHash = KnowledgeBaseService.sha256(new PathMatchingResourcePatternResolver()
                .getResource("classpath:knowledge/shipping.md")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        when(sources.findById(any())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            String hash = name.equals("shipping.md") ? shippingHash : "outdated";
            return Optional.of(new KnowledgeSource(name, hash, 3, TestData.NOW));
        });

        service.load();

        verify(vectorStore, times(3)).add(anyList());
        verify(vectorStore, times(3)).delete(any(Filter.Expression.class));
    }

    @Test
    void embeddingFailuresAreReportedNotThrown() {
        when(sources.findById(any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Connection refused")).when(vectorStore).add(anyList());

        KnowledgeBaseService.Status status = service.load();

        assertThat(status.state()).isEqualTo("FAILED");
        assertThat(status.error()).contains("Connection refused");
        assertThat(service.status()).isEqualTo(status);
    }
}
