package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import io.github.exceptionintelligence.server.scm.PrCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrCreationStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(PrCreationStep.class);
    private static final int ORDER = 50;

    private final PrCreator prCreator;

    public PrCreationStep(PrCreator prCreator) {
        this.prCreator = prCreator;
    }

    @Override
    public void execute(PipelineContext ctx) {
        if (ctx.getAnalysisResult() == null || !ctx.getAnalysisResult().hasFix()) {
            log.debug("[PR] No fixed source code in analysis — skipping PR creation");
            return;
        }

        StackFrame top = ctx.topProjectFrame();
        if (top == null) {
            log.debug("[PR] No top project frame — skipping PR creation");
            return;
        }

        try {
            String prUrl = prCreator.create(
                    ctx.getReport(),
                    ctx.getAnalysisResult(),
                    top,
                    ctx.getIssueNumber(),
                    ctx.getIssueUrl()
            );
            ctx.setPrUrl(prUrl);
            log.info("[PR] Created pull request — {}", prUrl);
        } catch (Exception ex) {
            log.warn("[PR] Failed to create pull request: {}", ex.getMessage());
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
