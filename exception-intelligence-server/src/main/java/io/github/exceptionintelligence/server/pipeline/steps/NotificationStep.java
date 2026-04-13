package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.notification.Notifier;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(NotificationStep.class);
    private static final int ORDER = 60;

    private final Notifier notifier;

    public NotificationStep(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void execute(PipelineContext ctx) {
        try {
            notifier.send(
                    ctx.getReport(),
                    ctx.getAnalysisResult(),
                    ctx.getIssueUrl(),
                    ctx.getPrUrl()
            );
        } catch (Exception ex) {
            log.warn("[Notification] Failed to send notification: {}", ex.getMessage());
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
