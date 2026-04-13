package io.github.exceptionintelligence.server.scm;

import io.github.exceptionintelligence.server.model.ExceptionReport;

import java.util.Optional;

/**
 * Fetches a window of source code from a SCM repository.
 */
public interface SourceCodeFetcher {

    /**
     * Fetches a code window centered around the given line number.
     *
     * <p>The {@code report} carries the repository coordinates (owner, name, branch)
     * sent by the SDK so the server can fetch from the correct repository even when
     * multiple services report exceptions to the same server instance.
     *
     * @param filePath   SCM-relative path (e.g. {@code src/main/java/com/example/Service.java})
     * @param lineNumber target line to center the window on
     * @param report     full exception report carrying repository information
     * @return optional result with content and the actual start line, empty if unavailable
     */
    Optional<SourceCodeResult> fetch(String filePath, int lineNumber, ExceptionReport report);

    record SourceCodeResult(String content, int startLine) {}
}
