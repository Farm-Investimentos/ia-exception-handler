package io.github.exceptionintelligence.server.ai;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.RequestContextModel;
import io.github.exceptionintelligence.server.model.StackFrame;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds LLM prompts (system + user) in a language-agnostic way.
 * The language/framework fields from the exception report drive the system prompt wording
 * and the source-code fence language identifier.
 */
@Component
public class LlmPromptBuilder {

    private final String responseLanguage;

    public LlmPromptBuilder(ServerProperties properties) {
        this.responseLanguage = properties.getResponseLanguage();
    }

    private static final String JSON_SCHEMA = """
            {
              "problemDescription": "<explanation of the root cause>",
              "suggestedFix": "<concise explanation of how to fix it, or null>",
              "fixedSourceCode": "<complete corrected source file content, or null>"
            }
            """;

    public String buildSystemPrompt(String language, String framework) {
        String lang = language != null && !language.isBlank() ? language : "software";
        String fwPart = (framework != null && !framework.isBlank())
                ? " specializing in " + framework + " applications"
                : "";

        return String.format("""
                You are an expert %s developer%s helping diagnose runtime exceptions.
                Analyze the provided exception, stack trace, source code, and request context.
                IMPORTANT: Always respond in %s, regardless of the language of the exception or source code.
                Respond ONLY with a valid JSON object matching this schema (no markdown, no text outside JSON):
                %s
                """, lang, fwPart, responseLanguage, JSON_SCHEMA);
    }

    public String buildUserMessage(LlmRequest req) {
        var sb = new StringBuilder();
        var report = req.report();
        String lang = report.language() != null ? report.language() : "unknown";

        // Exception details
        sb.append("## Exception\n");
        sb.append("Type: ").append(report.exception().type()).append("\n");
        if (report.exception().message() != null) {
            sb.append("Message: ").append(report.exception().message()).append("\n");
        }
        if (report.serviceName() != null) {
            sb.append("Service: ").append(report.serviceName()).append("\n");
        }
        if (report.environment() != null) {
            sb.append("Environment: ").append(report.environment()).append("\n");
        }
        sb.append("\n");

        // Stack frames (project code only, up to 10)
        sb.append("## Stack Trace (project frames)\n```\n");
        List<StackFrame> frames = req.projectFrames();
        if (frames != null && !frames.isEmpty()) {
            frames.stream().limit(10).forEach(f -> sb.append("  at ").append(f).append("\n"));
        } else if (report.exception().rawStackTrace() != null) {
            // Fallback: first 1500 chars of raw stack trace
            String raw = report.exception().rawStackTrace();
            sb.append(raw, 0, Math.min(1500, raw.length()));
            if (raw.length() > 1500) sb.append("\n  ... (truncated)");
            sb.append("\n");
        }
        sb.append("```\n\n");

        // Source code window
        if (req.sourceCode() != null && !req.sourceCode().isBlank()) {
            sb.append("## Source Code (starting at line ").append(req.sourceCodeStartLine()).append(")\n");
            sb.append("```").append(lang).append("\n");
            sb.append(req.sourceCode()).append("\n```\n\n");
        }

        // Request / browser context
        RequestContextModel ctx = report.request();
        if (ctx != null) {
            sb.append("## Request Context\n");
            if (ctx.method() != null && ctx.uri() != null) {
                sb.append("Endpoint: ").append(ctx.method()).append(" ").append(ctx.uri()).append("\n");
            }
            if (ctx.queryString() != null && !ctx.queryString().isBlank()) {
                sb.append("Query: ").append(ctx.queryString()).append("\n");
            }
            if (ctx.authenticatedUser() != null) {
                sb.append("User: ").append(ctx.authenticatedUser()).append("\n");
            }
            if (ctx.body() != null && !ctx.body().isBlank()) {
                sb.append("Request Body:\n```\n").append(ctx.body()).append("\n```\n");
            }
            if (ctx.headers() != null && !ctx.headers().isEmpty()) {
                sb.append("Headers:\n");
                ctx.headers().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            }
            sb.append("\n");
        }

        sb.append("Please analyze this exception and provide your response in the requested JSON format.");
        return sb.toString();
    }
}
