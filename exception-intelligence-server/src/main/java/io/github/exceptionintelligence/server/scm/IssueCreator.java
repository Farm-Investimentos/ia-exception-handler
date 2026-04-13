package io.github.exceptionintelligence.server.scm;

import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;

import java.util.List;

/**
 * Creates an issue in an issue tracker (GitHub Issues, Jira, …).
 */
public interface IssueCreator {

    CreatedIssue create(ExceptionReport report, AnalysisResult analysis, List<StackFrame> frames);

    /**
     * @param number     GitHub issue number (null for Jira — Jira uses string keys)
     * @param url        Full HTML URL of the created issue
     * @param identifier Human-readable identifier: "#123" for GitHub, "PROJ-42" for Jira
     */
    record CreatedIssue(Integer number, String url, String identifier) {
        /** Convenience constructor for GitHub where number == identifier prefix. */
        public CreatedIssue(int number, String url) {
            this(number, url, "#" + number);
        }
    }
}
