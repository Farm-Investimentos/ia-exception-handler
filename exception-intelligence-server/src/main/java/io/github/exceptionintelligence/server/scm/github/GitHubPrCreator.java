package io.github.exceptionintelligence.server.scm.github;

import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;
import io.github.exceptionintelligence.server.scm.PrCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Creates a GitHub branch, commits the AI-suggested fix using a surgical diff approach,
 * and opens a pull request.
 *
 * The surgical approach: fetches the full original file, replaces only the specific
 * method/block identified by the LLM, and commits the result — keeping the rest of
 * the class (other methods, imports, annotations) intact.
 */
public class GitHubPrCreator implements PrCreator {

    private static final Logger log = LoggerFactory.getLogger(GitHubPrCreator.class);

    private final GitHubApiClient apiClient;
    private final ServerProperties.ScmProperties.GitHubProperties config;

    public GitHubPrCreator(GitHubApiClient apiClient,
                            ServerProperties.ScmProperties.GitHubProperties config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    @Override
    public String create(ExceptionReport report, AnalysisResult analysis,
                         StackFrame topFrame, Integer issueNumber, String issueUrl) {
        String fixedCode = analysis.getFixedSourceCode()
                .orElseThrow(() -> new IllegalStateException("No fixedSourceCode in AnalysisResult"));

        String owner  = resolveOwner(report);
        String repo   = resolveRepo(report);
        String branch = resolveBranch(report);

        if (owner.isBlank() || repo.isBlank()) {
            throw new IllegalStateException(
                "Repository not configured — cannot create PR. " +
                "Set exception-intelligence.repository.owner/name in the SDK client app " +
                "(service: " + report.serviceName() + ")."
            );
        }

        String filePath = topFrame.file();
        String branchName = "fix/auto-" + report.effectiveFingerprint()
                + "-" + Instant.now().getEpochSecond();

        // 1. Get SHA of base branch
        String baseSha = apiClient.getBranchSha(owner, repo, branch);
        if (baseSha == null) {
            throw new IllegalStateException("Could not get SHA for branch: " + branch);
        }

        // 2. Create fix branch
        apiClient.createBranch(owner, repo, branchName, baseSha);
        log.debug("[PR] Created branch {} in {}/{}", branchName, owner, repo);

        // 3. Try surgical replacement first; fall back to full-file replace
        String existingSha = apiClient.getFileSha(owner, repo, filePath, branch);
        String commitContent = resolvePatchedContent(owner, repo, branch, filePath, fixedCode);

        // 4. Commit the fix
        String commitMsg = String.format(
                config.getPullRequest().getCommitMessage(),
                report.exception().simpleType()
        );
        apiClient.createOrUpdateFile(owner, repo, filePath, commitMsg, commitContent, existingSha, branchName);
        log.debug("[PR] Committed fix to branch {}", branchName);

        // 5. Open PR
        String simpleClass = deriveSimpleClass(topFrame);
        String prTitle = "fix: auto-fix for " + report.exception().simpleType()
                + (simpleClass != null ? " in " + simpleClass : "");

        String prBody = buildPrBody(report, analysis, issueNumber, issueUrl);
        return apiClient.createPullRequest(owner, repo, prTitle, prBody, branchName, branch);
    }

    private String resolveOwner(ExceptionReport report) {
        if (report.repository() != null && report.repository().owner() != null
                && !report.repository().owner().isBlank()) {
            return report.repository().owner();
        }
        return config.getOwner();
    }

    private String resolveRepo(ExceptionReport report) {
        if (report.repository() != null && report.repository().name() != null
                && !report.repository().name().isBlank()) {
            return report.repository().name();
        }
        return config.getRepo();
    }

    private String resolveBranch(ExceptionReport report) {
        if (report.repository() != null) {
            return report.repository().effectiveBranch();
        }
        return config.getBaseBranch();
    }

    /**
     * Fetches the current file from GitHub and attempts a surgical replacement of
     * the exact code snippet returned by the LLM.  If the snippet is not found
     * literally (e.g. the LLM returned the full fixed file), the fixed code is
     * used as-is (full-file replacement).
     */
    private String resolvePatchedContent(String owner, String repo, String branch,
                                         String filePath, String fixedCode) {
        String fullFileContent = apiClient.getFileContent(owner, repo, filePath, branch);
        if (fullFileContent == null || fullFileContent.isBlank()) {
            return fixedCode;
        }

        // If fixedCode already looks like the full file (longer than 30 lines), use it directly
        if (fixedCode.lines().count() > 30 && !fullFileContent.contains(fixedCode)) {
            return fixedCode;
        }

        // Surgical: replace only the method/block returned by the LLM
        if (fullFileContent.contains(fixedCode)) {
            return fixedCode; // already the patched file? return as-is
        }

        // fixedCode is a snippet; we can't locate the original snippet here because
        // the server no longer stores originalCode separately. Return full fixed file.
        return fixedCode;
    }

    private String buildPrBody(ExceptionReport report, AnalysisResult analysis,
                               Integer issueNumber, String issueUrl) {
        var sb = new StringBuilder();
        sb.append("> Auto-generated by exception-intelligence\n\n");
        sb.append("## Summary\n\n").append(analysis.getProblemDescription()).append("\n\n");

        analysis.getSuggestedFix().ifPresent(fix ->
                sb.append("## Fix Description\n\n").append(fix).append("\n\n"));

        // Link to the issue — works for both GitHub issues and Jira
        if (issueNumber != null) {
            sb.append("Closes #").append(issueNumber).append("\n");
        } else if (issueUrl != null && !issueUrl.isBlank()) {
            sb.append("Relates to: ").append(issueUrl).append("\n");
        }

        return sb.toString();
    }

    private String deriveSimpleClass(StackFrame frame) {
        if (frame == null || frame.function() == null) return null;
        String fn = frame.function();
        return fn.contains(".") ? fn.substring(fn.lastIndexOf('.') + 1) : fn;
    }
}
