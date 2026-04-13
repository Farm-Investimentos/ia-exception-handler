package io.github.exceptionintelligence.server.scm.jira;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for Atlassian Document Format (ADF) — the rich text format
 * required by the Jira Cloud REST API v3 for issue descriptions.
 */
public class AdfBuilder {

    private final List<Map<String, Object>> content = new ArrayList<>();

    public AdfBuilder heading(int level, String text) {
        content.add(Map.of(
                "type", "heading",
                "attrs", Map.of("level", level),
                "content", List.of(textNode(text))
        ));
        return this;
    }

    public AdfBuilder paragraph(String text) {
        if (text == null || text.isBlank()) return this;
        content.add(Map.of(
                "type", "paragraph",
                "content", List.of(textNode(text))
        ));
        return this;
    }

    public AdfBuilder boldLine(String label, String value) {
        if (value == null || value.isBlank()) return this;
        content.add(Map.of(
                "type", "paragraph",
                "content", List.of(
                        Map.of("type", "text", "text", label + ": ",
                               "marks", List.of(Map.of("type", "strong"))),
                        Map.of("type", "text", "text", value)
                )
        ));
        return this;
    }

    public AdfBuilder codeBlock(String language, String code) {
        if (code == null || code.isBlank()) return this;
        var attrs = language != null && !language.isBlank()
                ? Map.of("language", language)
                : Map.of("language", "text");
        content.add(Map.of(
                "type", "codeBlock",
                "attrs", attrs,
                "content", List.of(textNode(code))
        ));
        return this;
    }

    public AdfBuilder rule() {
        content.add(Map.of("type", "rule"));
        return this;
    }

    public Map<String, Object> build() {
        return Map.of("type", "doc", "version", 1, "content", content);
    }

    private Map<String, Object> textNode(String text) {
        return Map.of("type", "text", "text", text);
    }
}
