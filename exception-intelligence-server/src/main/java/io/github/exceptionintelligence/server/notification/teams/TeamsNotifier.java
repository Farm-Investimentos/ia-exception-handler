package io.github.exceptionintelligence.server.notification.teams;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.notification.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends exception alerts to a Microsoft Teams channel via Incoming Webhook.
 *
 * Auto-detects the webhook type by URL:
 * - Classic Incoming Webhook (webhook.office.com) → MessageCard format (free, no Power Automate).
 * - Teams Workflow Webhook (logic.azure.com / powerautomate.com) → Adaptive Card format.
 *
 * Never throws exceptions — all errors are logged and swallowed so Teams being down
 * cannot abort the pipeline.
 */
public class TeamsNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(TeamsNotifier.class);

    private final ServerProperties.NotificationProperties.TeamsProperties config;
    private final RestTemplate restTemplate;

    public TeamsNotifier(ServerProperties.NotificationProperties.TeamsProperties config) {
        this.config = config;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(rf);
    }

    @Override
    public void send(ExceptionReport report, AnalysisResult analysis, String issueUrl, String prUrl) {
        if (!config.isEnabled() || config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            return;
        }

        try {
            boolean isClassicWebhook = config.getWebhookUrl().contains("webhook.office.com");
            var payload = isClassicWebhook
                    ? buildMessageCardPayload(report, analysis, issueUrl, prUrl)
                    : buildAdaptiveCardPayload(report, analysis, issueUrl, prUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(config.getWebhookUrl(), entity, Void.class);

            log.debug("[Teams] Notification sent (type: {}).",
                    isClassicWebhook ? "MessageCard" : "AdaptiveCard");
        } catch (Exception ex) {
            log.warn("[Teams] Failed to send notification: {}", ex.getMessage());
        }
    }

    /** MessageCard format — for classic Incoming Webhook (webhook.office.com). Free, no Power Automate needed. */
    private Map<String, Object> buildMessageCardPayload(ExceptionReport report, AnalysisResult analysis,
                                                         String issueUrl, String prUrl) {
        String exType = report.exception().simpleType();
        String timestamp = formatTimestamp(report);

        var facts = new ArrayList<Map<String, String>>();
        facts.add(Map.of("name", "Exception", "value", exType));
        if (report.serviceName() != null) facts.add(Map.of("name", "Service", "value", report.serviceName()));
        if (report.environment() != null) facts.add(Map.of("name", "Environment", "value", report.environment()));
        facts.add(Map.of("name", "Language", "value", report.language()));
        facts.add(Map.of("name", "Time", "value", timestamp));

        StackFrame top = report.topProjectFrame();
        if (top != null) {
            facts.add(Map.of("name", "Location", "value", top.toString()));
        }
        if (report.request() != null && report.request().method() != null) {
            facts.add(Map.of("name", "Endpoint",
                    "value", report.request().method() + " " + report.request().uri()));
        }
        if (report.request() != null && report.request().authenticatedUser() != null) {
            facts.add(Map.of("name", "User", "value", report.request().authenticatedUser()));
        }

        var section = new LinkedHashMap<String, Object>();
        section.put("activityTitle", "Exception Detected: " + exType);
        if (analysis != null && analysis.getProblemDescription() != null) {
            section.put("activityText", truncate(analysis.getProblemDescription(), 500));
        }
        section.put("facts", facts);
        section.put("markdown", true);

        var actions = new ArrayList<Map<String, Object>>();
        if (issueUrl != null) {
            actions.add(Map.of(
                    "@type", "OpenUri", "name", "View Issue",
                    "targets", List.of(Map.of("os", "default", "uri", issueUrl))));
        }
        if (prUrl != null) {
            actions.add(Map.of(
                    "@type", "OpenUri", "name", "View Pull Request",
                    "targets", List.of(Map.of("os", "default", "uri", prUrl))));
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "https://schema.org/extensions");
        payload.put("themeColor", "FF0000");
        payload.put("summary", "Exception Detected: " + exType);
        payload.put("sections", List.of(section));
        if (!actions.isEmpty()) payload.put("potentialAction", actions);
        return payload;
    }

    /** Adaptive Card format — for Teams Workflow Webhook (Power Automate). */
    private Map<String, Object> buildAdaptiveCardPayload(ExceptionReport report, AnalysisResult analysis,
                                                          String issueUrl, String prUrl) {
        String exType = report.exception().simpleType();
        String timestamp = formatTimestamp(report);

        var facts = new ArrayList<Map<String, String>>();
        facts.add(Map.of("title", "Exception", "value", exType));
        if (report.serviceName() != null) facts.add(Map.of("title", "Service", "value", report.serviceName()));
        if (report.environment() != null) facts.add(Map.of("title", "Environment", "value", report.environment()));
        facts.add(Map.of("title", "Language", "value", report.language()));
        facts.add(Map.of("title", "Time", "value", timestamp));

        StackFrame top = report.topProjectFrame();
        if (top != null) {
            facts.add(Map.of("title", "Location", "value", top.toString()));
        }
        if (report.request() != null && report.request().method() != null) {
            facts.add(Map.of("title", "Endpoint",
                    "value", report.request().method() + " " + report.request().uri()));
        }
        if (report.request() != null && report.request().authenticatedUser() != null) {
            facts.add(Map.of("title", "User", "value", report.request().authenticatedUser()));
        }

        var body = new ArrayList<>();
        body.add(Map.of(
                "type", "TextBlock",
                "text", "Exception Intelligence Alert",
                "weight", "Bolder",
                "size", "Medium",
                "color", "Attention"
        ));
        body.add(Map.of("type", "FactSet", "facts", facts));

        if (analysis != null && analysis.getProblemDescription() != null) {
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "**Analysis:** " + truncate(analysis.getProblemDescription(), 500),
                    "wrap", true
            ));
        }

        var actions = new ArrayList<>();
        if (issueUrl != null) {
            actions.add(Map.of("type", "Action.OpenUrl", "title", "View Issue", "url", issueUrl));
        }
        if (prUrl != null) {
            actions.add(Map.of("type", "Action.OpenUrl", "title", "View Pull Request", "url", prUrl));
        }

        var card = new LinkedHashMap<String, Object>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);
        if (!actions.isEmpty()) card.put("actions", actions);

        var attachment = Map.of(
                "contentType", "application/vnd.microsoft.card.adaptive",
                "content", card
        );
        return Map.of("type", "message", "attachments", List.of(attachment));
    }

    private String formatTimestamp(ExceptionReport report) {
        return report.timestamp() != null
                ? DateTimeFormatter.ISO_INSTANT.format(report.timestamp().atOffset(ZoneOffset.UTC))
                : "unknown";
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
