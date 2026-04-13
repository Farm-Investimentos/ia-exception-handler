package io.github.exceptionintelligence.server.scm.github;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.RequestContextModel;
import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.scm.IssueCreator;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Creates a comprehensive GitHub issue with exception details, request context and AI analysis.
 */
public class GitHubIssueCreator implements IssueCreator {

    private final GitHubApiClient apiClient;
    private final ServerProperties.ScmProperties.GitHubProperties config;

    public GitHubIssueCreator(GitHubApiClient apiClient,
                               ServerProperties.ScmProperties.GitHubProperties config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    @Override
    public CreatedIssue create(ExceptionReport report, AnalysisResult analysis, List<StackFrame> frames) {
        String owner = resolveOwner(report);
        String repo  = resolveRepo(report);
        String title = buildTitle(report);
        String body  = buildBody(report, analysis, frames);
        return apiClient.createIssue(owner, repo, title, body, config.getLabels());
    }

    private String resolveOwner(ExceptionReport report) {
        if (report.repository() != null && report.repository().owner() != null
                && !report.repository().owner().isBlank()) {
            return report.repository().owner();
        }
        return config.getOwner();
    }

    private String resolveRepo(ExceptionReport report) {
        if (report.repository() != null && report.repository().name() != null
                && !report.repository().name().isBlank()) {
            return report.repository().name();
        }
        return config.getRepo();
    }

    private String buildTitle(ExceptionReport report) {
        StackFrame top = report.topProjectFrame();
        String location = top != null
                ? top.function() + ":" + top.line()
                : report.exception().type();
        return config.getIssue().getTitlePrefix() + " "
                + report.exception().simpleType() + " in " + location;
    }

    private String buildBody(ExceptionReport report, AnalysisResult analysis, List<StackFrame> frames) {
        var sb = new StringBuilder();
        String ts = DateTimeFormatter.ISO_INSTANT.format(report.timestamp().atOffset(ZoneOffset.UTC));

        sb.append("> **Auto-detected by exception-intelligence** | `").append(ts).append("`\n\n");

        // Service / language metadata
        sb.append("| Field | Value |\n|---|---|\n");
        if (report.serviceName() != null) sb.append("| Service | `").append(report.serviceName()).append("` |\n");
        if (report.environment() != null) sb.append("| Environment | `").append(report.environment()).append("` |\n");
        sb.append("| Language | `").append(report.language()).append("` |\n");
        if (report.framework() != null) sb.append("| Framework | `").append(report.framework()).append("` |\n");
        sb.append("\n");

        // Exception
        sb.append("## Exception\n\n");
        sb.append("**Type:** `").append(report.exception().type()).append("`\n\n");
        if (report.exception().message() != null) {
            sb.append("**Message:** ").append(report.exception().message()).append("\n\n");
        }

        // Request context
        RequestContextModel req = report.request();
        if (req != null) {
            sb.append("## Request Context\n\n");
            if (req.method() != null && req.uri() != null) {
                sb.append("**Endpoint:** `").append(req.method()).append(" ").append(req.uri()).append("`\n\n");
            }
            if (req.authenticatedUser() != null) {
                sb.append("**User:** `").append(req.authenticatedUser()).append("`\n\n");
            }
            if (req.queryString() != null && !req.queryString().isBlank()) {
                sb.append("**Query:** `").append(req.queryString()).append("`\n\n");
            }
            if (req.body() != null && !req.body().isBlank()) {
                sb.append("**Request Body:**\n```\n").append(req.body()).append("\n```\n\n");
            }
        }

        // Stack trace
        sb.append("## Stack Trace\n\n");
        if (frames != null && !frames.isEmpty()) {
            sb.append("**Project frames:**\n```\n");
            frames.stream().limit(15).forEach(f -> sb.append("  at ").append(f).append("\n"));
            sb.append("```\n\n");
        }
        if (report.exception().rawStackTrace() != null) {
            sb.append("<details><summary>Full stack trace</summary>\n\n```\n");
            sb.append(report.exception().rawStackTrace());
            sb.append("\n```\n</details>\n\n");
        }

        // AI Analysis
        if (analysis != null) {
            sb.append("## AI Analysis\n\n");
            sb.append(analysis.getProblemDescription()).append("\n\n");
            analysis.getSuggestedFix().ifPresent(fix ->
                    sb.append("### Suggested Fix\n\n").append(fix).append("\n\n"));
            analysis.getFixedSourceCode().ifPresent(code ->
                    sb.append("### Proposed Code Change\n\n```")
                      .append(report.language()).append("\n")
                      .append(code).append("\n```\n\n"));
        }

        // Metadata
        sb.append("---\n");
        sb.append("*Fingerprint:* `").append(report.effectiveFingerprint()).append("`  \n");
        if (report.threadName() != null) {
            sb.append("*Thread:* `").append(report.threadName()).append("`");
        }

        return sb.toString();
    }
}
