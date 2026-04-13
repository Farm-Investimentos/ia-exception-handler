package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.dedup.DeduplicationService;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeduplicationStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationStep.class);
    private static final int ORDER = 10;

    private final DeduplicationService deduplicationService;

    public DeduplicationStep(DeduplicationService deduplicationService) {
        this.deduplicationService = deduplicationService;
    }

    @Override
    public void execute(PipelineContext ctx) {
        String fingerprint = ctx.getReport().effectiveFingerprint();

        if (deduplicationService.checkAndMark(fingerprint)) {
            log.info("[Dedup] Duplicate fingerprint={} — aborting pipeline", fingerprint);
            ctx.abort("Duplicate exception fingerprint: " + fingerprint);
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
