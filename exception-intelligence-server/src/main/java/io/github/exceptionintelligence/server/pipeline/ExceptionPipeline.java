package io.github.exceptionintelligence.server.pipeline;

import io.github.exceptionintelligence.server.model.ExceptionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the exception analysis pipeline.
 * Each report is processed asynchronously through a sequence of {@link PipelineStep}s.
 */
@Service
public class ExceptionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ExceptionPipeline.class);

    private final List<PipelineStep> steps;

    public ExceptionPipeline(List<PipelineStep> steps) {
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(PipelineStep::getOrder))
                .toList();
    }

    @Async("exceptionIntelligenceExecutor")
    public void process(ExceptionReport report) {
        log.info("[Pipeline] Processing exception report — service={} type={} fingerprint={}",
                report.serviceName(), report.exception().type(), report.effectiveFingerprint());

        PipelineContext ctx = new PipelineContext(report);

        for (PipelineStep step : steps) {
            if (ctx.isAborted()) {
                log.debug("[Pipeline] Aborted after step order={} reason={}",
                        step.getOrder(), ctx.getAbortReason());
                break;
            }
            try {
                step.execute(ctx);
            } catch (Exception ex) {
                log.warn("[Pipeline] Step {} failed — continuing pipeline: {}",
                        step.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }

        log.info("[Pipeline] Finished — service={} issueUrl={} prUrl={}",
                report.serviceName(), ctx.getIssueUrl(), ctx.getPrUrl());
    }
}
