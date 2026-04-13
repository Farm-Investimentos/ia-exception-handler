package io.github.exceptionintelligence.server.notification;

import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;

/**
 * Strategy interface for notification channels (Teams, Slack, …).
 */
public interface Notifier {

    /**
     * Sends a notification about the analyzed exception.
     *
     * @param report   the original exception report
     * @param analysis LLM analysis result (may be null or unavailable)
     * @param issueUrl URL of the created issue (may be null)
     * @param prUrl    URL of the created PR (may be null)
     */
    void send(ExceptionReport report, AnalysisResult analysis, String issueUrl, String prUrl);
}
