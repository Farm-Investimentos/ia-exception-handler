package io.github.exceptionintelligence.server.scm;

import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;

/**
 * Creates a pull / merge request in a SCM system with the AI-suggested fix.
 */
public interface PrCreator {

    /**
     * @param report      the exception report
     * @param analysis    LLM analysis result (must have {@code fixedSourceCode})
     * @param topFrame    the top project stack frame (determines which file to update)
     * @param issueNumber GitHub issue number for "Closes #N" (null when using Jira)
     * @param issueUrl    full issue URL — used to cross-reference Jira issues in the PR body
     * @return HTML URL of the created PR
     */
    String create(ExceptionReport report, AnalysisResult analysis,
                  StackFrame topFrame, Integer issueNumber, String issueUrl);
}
