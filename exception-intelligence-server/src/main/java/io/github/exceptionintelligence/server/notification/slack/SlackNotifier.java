package io.github.exceptionintelligence.server.notification.slack;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.notification.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends exception alerts to Slack via Incoming Webhook (Block Kit).
 * Activated when {@code exception-intelligence.notification.provider=slack}.
 */
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final ServerProperties.NotificationProperties.SlackProperties config;
    private final RestClient restClient;

    public SlackNotifier(ServerProperties.NotificationProperties.SlackProperties config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public void send(ExceptionReport report, AnalysisResult analysis, String issueUrl, String prUrl) {
        if (!config.isEnabled() || config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            return;
        }

        try {
            var blocks = new ArrayList<>();
            blocks.add(Map.of(
                    "type", "header",
                    "text", Map.of("type", "plain_text",
                            "text", "Exception Intelligence Alert: " + report.exception().simpleType())
            ));

            var fields = buildFields(report);
            blocks.add(Map.of("type", "section", "fields", fields));

            if (analysis != null && analysis.getProblemDescription() != null) {
                blocks.add(Map.of(
                        "type", "section",
                        "text", Map.of("type", "mrkdwn",
                                "text", "*Analysis:* " + truncate(analysis.getProblemDescription(), 500))
                ));
            }

            var actions = buildActions(issueUrl, prUrl);
            if (!actions.isEmpty()) {
                blocks.add(Map.of("type", "actions", "elements", actions));
            }

            restClient.post()
                    .uri(config.getWebhookUrl())
                    .body(Map.of("blocks", blocks))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("[Slack] Failed to send notification: {}", ex.getMessage());
        }
    }

    private List<Map<String, Object>> buildFields(ExceptionReport report) {
        var fields = new ArrayList<Map<String, Object>>();
        fields.add(field("*Exception*\n`" + report.exception().type() + "`"));
        if (report.serviceName() != null) fields.add(field("*Service*\n" + report.serviceName()));
        if (report.environment() != null) fields.add(field("*Environment*\n" + report.environment()));
        fields.add(field("*Language*\n" + report.language()));
        if (report.timestamp() != null) {
            fields.add(field("*Time*\n" +
                    DateTimeFormatter.ISO_INSTANT.format(report.timestamp().atOffset(ZoneOffset.UTC))));
        }
        if (report.request() != null && report.request().uri() != null) {
            fields.add(field("*Endpoint*\n" + report.request().method() + " " + report.request().uri()));
        }
        return fields;
    }

    private List<Map<String, Object>> buildActions(String issueUrl, String prUrl) {
        var actions = new ArrayList<Map<String, Object>>();
        if (issueUrl != null) {
            actions.add(Map.of("type", "button", "text",
                    Map.of("type", "plain_text", "text", "View Issue"), "url", issueUrl));
        }
        if (prUrl != null) {
            actions.add(Map.of("type", "button", "text",
                    Map.of("type", "plain_text", "text", "View PR"), "url", prUrl));
        }
        return actions;
    }

    private Map<String, Object> field(String text) {
        return Map.of("type", "mrkdwn", "text", text);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
