package io.github.exceptionintelligence.server.pipeline.steps;

import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.pipeline.PipelineContext;
import io.github.exceptionintelligence.server.pipeline.PipelineStep;
import io.github.exceptionintelligence.server.scm.IssueCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IssueCreationStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(IssueCreationStep.class);
    private static final int ORDER = 40;

    private final IssueCreator issueCreator;

    public IssueCreationStep(IssueCreator issueCreator) {
        this.issueCreator = issueCreator;
    }

    @Override
    public void execute(PipelineContext ctx) {
        AnalysisResult analysis = ctx.getAnalysisResult();
        try {
            IssueCreator.CreatedIssue issue = issueCreator.create(
                    ctx.getReport(), analysis, ctx.projectFrames());
            ctx.setIssueUrl(issue.url());
            ctx.setIssueNumber(issue.number());
            log.info("[Issue] Created issue {} — {}", issue.identifier(), issue.url());
        } catch (Exception ex) {
            log.warn("[Issue] Failed to create issue: {}", ex.getMessage());
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
