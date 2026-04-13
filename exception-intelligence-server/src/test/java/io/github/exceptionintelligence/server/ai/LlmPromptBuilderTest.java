package io.github.exceptionintelligence.server.ai;

import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptBuilderTest {

    private final LlmPromptBuilder builder = new LlmPromptBuilder();

    @Test
    void systemPromptContainsLanguage() {
        String prompt = builder.buildSystemPrompt("java", "spring-boot");
        assertThat(prompt).contains("java");
        assertThat(prompt).contains("spring-boot");
    }

    @Test
    void systemPromptForUnknownLanguageIsGeneric() {
        String prompt = builder.buildSystemPrompt(null, null);
        assertThat(prompt).contains("software");
    }

    @Test
    void userMessageContainsExceptionType() {
        LlmRequest req = new LlmRequest(sampleReport("python", "django"), List.of(), null, 0);
        String msg = builder.buildUserMessage(req);
        assertThat(msg).contains("ValueError");
    }

    @Test
    void userMessageContainsSourceCodeFenceWithLanguage() {
        ExceptionReport report = sampleReport("python", "django");
        LlmRequest req = new LlmRequest(report, List.of(), "def foo():\n    pass", 10);
        String msg = builder.buildUserMessage(req);
        assertThat(msg).contains("```python");
    }

    @Test
    void userMessageContainsRequestContext() {
        ExceptionReport report = sampleReport("javascript", "express");
        LlmRequest req = new LlmRequest(report, List.of(), null, 0);
        String msg = builder.buildUserMessage(req);
        assertThat(msg).contains("POST");
        assertThat(msg).contains("/api/orders");
    }

    private ExceptionReport sampleReport(String language, String framework) {
        return new ExceptionReport(
                language, framework, "test-svc", "test",
                Instant.now(), "main",
                new ExceptionReport.ExceptionInfo(
                        "ValueError", "invalid input", null,
                        List.of(new StackFrame("src/views.py", "views.process", 42, null, true))
                ),
                new io.github.exceptionintelligence.server.model.RequestContextModel(
                        "POST", "/api/orders", null, null, "{\"id\":1}", null),
                "fp12"
        );
    }
}
