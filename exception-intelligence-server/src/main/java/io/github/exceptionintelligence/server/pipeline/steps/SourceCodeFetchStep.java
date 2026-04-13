package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import io.github.exceptionintelligence.server.scm.SourceCodeFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceCodeFetchStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeFetchStep.class);
    private static final int ORDER = 20;

    private final SourceCodeFetcher fetcher;

    public SourceCodeFetchStep(SourceCodeFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public void execute(PipelineContext ctx) {
        StackFrame top = ctx.topProjectFrame();
        if (top == null || top.file() == null || top.file().isBlank()) {
            log.debug("[SourceFetch] No top project frame — skipping source code fetch");
            return;
        }

        try {
            fetcher.fetch(top.file(), top.line(), ctx.getReport()).ifPresent(result -> {
                ctx.setSourceCode(result.content());
                ctx.setSourceCodeStartLine(result.startLine());
                log.debug("[SourceFetch] Fetched {} chars from {} starting at line {}",
                        result.content().length(), top.file(), result.startLine());
            });
        } catch (Exception ex) {
            log.warn("[SourceFetch] Could not fetch source for {} — continuing without it: {}",
                    top.file(), ex.getMessage());
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
