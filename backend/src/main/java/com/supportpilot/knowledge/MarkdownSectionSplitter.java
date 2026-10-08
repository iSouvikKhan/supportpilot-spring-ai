package com.supportpilot.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.document.Document;

/**
 * Splits a Markdown knowledge-base article into one {@link Document} per "## " section. Each
 * section keeps the article title so it still makes sense on its own. Sections longer than
 * {@code maxChars} are split further on paragraph boundaries.
 */
public final class MarkdownSectionSplitter {

    private final int maxChars;

    public MarkdownSectionSplitter(int maxChars) {
        if (maxChars < 200) {
            throw new IllegalArgumentException("maxChars must be at least 200");
        }
        this.maxChars = maxChars;
    }

    public List<Document> split(String source, String markdown) {
        String text = markdown.replace("\r\n", "\n").strip();
        String title = source;
        List<String[]> sections = new ArrayList<>(); // [heading, body]
        String heading = null;
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\n")) {
            if (line.startsWith("# ")) {
                title = line.substring(2).strip();
            } else if (line.startsWith("## ")) {
                addSection(sections, heading, body);
                heading = line.substring(3).strip();
                body = new StringBuilder();
            } else {
                body.append(line).append('\n');
            }
        }
        addSection(sections, heading, body);

        List<Document> documents = new ArrayList<>();
        for (String[] section : sections) {
            String prefix = section[0] == null ? title : title + " - " + section[0];
            for (String part : chunk(section[1])) {
                int index = documents.size();
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", source);
                metadata.put("title", prefix);
                metadata.put("chunk", index);
                String id = UUID.nameUUIDFromBytes((source + "#" + index).getBytes(StandardCharsets.UTF_8))
                        .toString();
                documents.add(new Document(id, prefix + "\n\n" + part, metadata));
            }
        }
        return documents;
    }

    private static void addSection(List<String[]> sections, String heading, StringBuilder body) {
        String content = body.toString().strip();
        if (!content.isEmpty()) {
            sections.add(new String[] {heading, content});
        }
    }

    private List<String> chunk(String body) {
        if (body.length() <= maxChars) {
            return List.of(body);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : body.split("\n\\s*\n")) {
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > maxChars) {
                parts.add(current.toString().strip());
                current = new StringBuilder();
            }
            current.append(paragraph).append("\n\n");
        }
        if (current.length() > 0) {
            parts.add(current.toString().strip());
        }
        return parts;
    }
}
