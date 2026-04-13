package io.github.exceptionintelligence.server.pipeline;

import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;

import java.util.List;

/**
 * Mutable context object passed through all pipeline steps.
 * Steps read and write to this context to share intermediate results.
 */
public class PipelineContext {

    private final ExceptionReport report;

    // Populated by DeduplicationStep
    private boolean aborted = false;
    private String abortReason;

    // Populated by SourceCodeFetchStep
    private String sourceCode;
    private int sourceCodeStartLine;

    // Populated by LlmAnalysisStep
    private AnalysisResult analysisResult;

    // Populated by IssueCreationStep
    private String issueUrl;
    private Integer issueNumber;

    // Populated by PrCreationStep
    private String prUrl;

    public PipelineContext(ExceptionReport report) {
        this.report = report;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public ExceptionReport getReport() { return report; }

    public boolean isAborted() { return aborted; }

    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    public String getAbortReason() { return abortReason; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public int getSourceCodeStartLine() { return sourceCodeStartLine; }
    public void setSourceCodeStartLine(int line) { this.sourceCodeStartLine = line; }

    public AnalysisResult getAnalysisResult() { return analysisResult; }
    public void setAnalysisResult(AnalysisResult result) { this.analysisResult = result; }

    public String getIssueUrl() { return issueUrl; }
    public void setIssueUrl(String url) { this.issueUrl = url; }

    public Integer getIssueNumber() { return issueNumber; }
    public void setIssueNumber(Integer number) { this.issueNumber = number; }

    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String url) { this.prUrl = url; }

    // ── Convenience delegates to ExceptionReport ──────────────────────────

    public List<StackFrame> projectFrames() { return report.projectFrames(); }
    public StackFrame topProjectFrame() { return report.topProjectFrame(); }
}
