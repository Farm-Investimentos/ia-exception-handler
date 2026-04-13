package io.github.exceptionintelligence.server.scm.jira;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.RequestContextModel;
import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.scm.IssueCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Issue tracker implementation backed by Jira Cloud (REST API v3).
 * Pull requests are created on GitHub by PrCreationStep — the Jira issue key
 * is included in the PR body for cross-referencing.
 */
public class JiraIssueCreator implements IssueCreator {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueCreator.class);

    private final JiraApiClient jiraApiClient;

    public JiraIssueCreator(ServerProperties.IssueTrackerProperties.JiraProperties config) {
        this.jiraApiClient = new JiraApiClient(config);
    }

    @Override
    public CreatedIssue create(ExceptionReport report, AnalysisResult analysis, List<StackFrame> frames) {
        String summary = buildSummary(report);
        var description = buildDescription(report, analysis, frames);

        var created = jiraApiClient.createIssue(summary, description);
        log.info("[Jira] Issue criada: {}", created.url());

        // issueNumber = null para Jira — o PR body usará issueUrl para cross-referencing
        return new CreatedIssue(null, created.url(), created.key());
    }

    private String buildSummary(ExceptionReport report) {
        StackFrame top = report.topProjectFrame();
        String location = top != null
                ? top.function() + ":" + top.line()
                : report.exception().type();
        return "[AUTO] " + report.exception().simpleType() + " in " + location;
    }

    private java.util.Map<String, Object> buildDescription(ExceptionReport report,
                                                            AnalysisResult analysis,
                                                            List<StackFrame> frames) {
        String timestamp = report.timestamp() != null
                ? DateTimeFormatter.ISO_INSTANT.format(report.timestamp().atOffset(ZoneOffset.UTC))
                : "unknown";

        var adf = new AdfBuilder();

        adf.paragraph("Detectado automaticamente pelo exception-intelligence | " + timestamp);

        if (report.serviceName() != null) {
            adf.boldLine("Serviço", report.serviceName());
        }
        if (report.environment() != null) {
            adf.boldLine("Ambiente", report.environment());
        }
        adf.boldLine("Linguagem", report.language());
        if (report.framework() != null) {
            adf.boldLine("Framework", report.framework());
        }

        adf.heading(2, "Exceção");
        adf.boldLine("Tipo", report.exception().type());
        if (report.exception().message() != null) {
            adf.boldLine("Mensagem", report.exception().message());
        }

        RequestContextModel req = report.request();
        if (req != null) {
            adf.heading(2, "Contexto da Requisição HTTP");
            if (req.method() != null && req.uri() != null) {
                adf.boldLine("Endpoint", req.method() + " " + req.uri());
            }
            if (req.authenticatedUser() != null) {
                adf.boldLine("Usuário", req.authenticatedUser());
            }
            if (req.queryString() != null && !req.queryString().isBlank()) {
                adf.boldLine("Query String", req.queryString());
            }
            if (req.body() != null && !req.body().isBlank()) {
                adf.paragraph("Corpo da Requisição:");
                adf.codeBlock("json", req.body());
            }
        }

        adf.heading(2, "Stack Trace");
        if (frames != null && !frames.isEmpty()) {
            var sb = new StringBuilder();
            frames.stream().limit(15).forEach(f -> sb.append("  at ").append(f).append("\n"));
            adf.codeBlock("text", sb.toString().trim());
        }
        if (report.exception().rawStackTrace() != null && !report.exception().rawStackTrace().isBlank()) {
            adf.paragraph("Stack trace completo:");
            adf.codeBlock("text", report.exception().rawStackTrace());
        }

        if (analysis != null) {
            adf.heading(2, "Análise da IA");
            adf.paragraph(analysis.getProblemDescription());

            analysis.getSuggestedFix().ifPresent(fix -> {
                adf.heading(3, "Correção Sugerida");
                adf.paragraph(fix);
            });

            analysis.getFixedSourceCode().ifPresent(code -> {
                adf.heading(3, "Código Corrigido");
                adf.codeBlock(report.language(), code);
            });
        }

        adf.rule();
        adf.paragraph("Fingerprint: " + report.effectiveFingerprint()
                + (report.threadName() != null ? "  |  Thread: " + report.threadName() : ""));

        return adf.build();
    }
}
