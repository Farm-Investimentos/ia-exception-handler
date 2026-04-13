package io.github.exceptionintelligence.server.scm.github;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.scm.SourceCodeFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

/**
 * Fetches a context window of source code from a GitHub repository.
 *
 * Repository coordinates (owner / name / branch) are resolved from the exception
 * report sent by the SDK, with the server-level config used only as a fallback.
 */
public class GitHubSourceCodeFetcher implements SourceCodeFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubSourceCodeFetcher.class);

    private final GitHubApiClient apiClient;
    private final ServerProperties.ScmProperties.GitHubProperties config;
    private final int contextLines;

    public GitHubSourceCodeFetcher(GitHubApiClient apiClient,
                                   ServerProperties.ScmProperties.GitHubProperties config,
                                   int contextLines) {
        this.apiClient = apiClient;
        this.config = config;
        this.contextLines = contextLines;
    }

    @Override
    public Optional<SourceCodeResult> fetch(String filePath, int lineNumber, ExceptionReport report) {
        String owner  = resolveOwner(report);
        String repo   = resolveRepo(report);
        String branch = resolveBranch(report);

        if (owner.isBlank() || repo.isBlank()) {
            log.warn("[SourceFetch] Repository not configured — skipping source fetch. " +
                     "Set exception-intelligence.repository.owner/name in the SDK client app.");
            return Optional.empty();
        }

        String raw = apiClient.getFileContent(owner, repo, filePath, branch);
        if (raw == null || raw.isBlank()) return Optional.empty();

        String[] lines = raw.split("\n");
        int startLine = Math.max(1, lineNumber - contextLines);
        int endLine   = Math.min(lines.length, lineNumber + contextLines);

        String window = String.join("\n",
                Arrays.copyOfRange(lines, startLine - 1, endLine));

        return Optional.of(new SourceCodeResult(window, startLine));
    }

    // ── Resolution helpers (report wins, server config as fallback) ───────

    String resolveOwner(ExceptionReport report) {
        if (report != null && report.repository() != null
                && report.repository().owner() != null
                && !report.repository().owner().isBlank()) {
            return report.repository().owner();
        }
        return config.getOwner();
    }

    String resolveRepo(ExceptionReport report) {
        if (report != null && report.repository() != null
                && report.repository().name() != null
                && !report.repository().name().isBlank()) {
            return report.repository().name();
        }
        return config.getRepo();
    }

    String resolveBranch(ExceptionReport report) {
        if (report != null && report.repository() != null) {
            return report.repository().effectiveBranch();
        }
        return config.getBaseBranch();
    }
}
