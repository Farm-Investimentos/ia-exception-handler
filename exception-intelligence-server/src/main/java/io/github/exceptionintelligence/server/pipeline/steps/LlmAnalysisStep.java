package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.ai.LlmClient;
import io.github.exceptionintelligence.server.ai.LlmRequest;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmAnalysisStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisStep.class);
    private static final int ORDER = 30;

    private final LlmClient llmClient;

    public LlmAnalysisStep(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public void execute(PipelineContext ctx) {
        LlmRequest request = new LlmRequest(
                ctx.getReport(),
                ctx.projectFrames(),
                ctx.getSourceCode(),
                ctx.getSourceCodeStartLine()
        );

        try {
            AnalysisResult result = llmClient.analyze(request);
            ctx.setAnalysisResult(result);
            log.debug("[LLM] Analysis complete — hasFix={}", result.hasFix());
        } catch (Exception ex) {
            log.warn("[LLM] Analysis failed — storing unavailable result: {}", ex.getMessage());
            ctx.setAnalysisResult(AnalysisResult.unavailable());
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
