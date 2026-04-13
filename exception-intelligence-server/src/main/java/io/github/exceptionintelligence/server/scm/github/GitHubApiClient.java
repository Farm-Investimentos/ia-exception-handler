package io.github.exceptionintelligence.server.scm.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.scm.IssueCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level GitHub REST API v3 client.
 *
 * Owner and repo are passed explicitly to every method so the server can serve
 * multiple client applications — each SDK sends its own repository coordinates
 * in the exception report payload, and the server uses them for source fetching,
 * issue creation, and PR creation without any hardcoded per-service config.
 *
 * The server's {@code github.owner} / {@code github.repo} config values are used
 * only as fallbacks when a report does not include repository information.
 */
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final RestClient restClient;
    /** Fallback owner when the exception report does not carry repository info. */
    final ServerProperties.ScmProperties.GitHubProperties config;

    public GitHubApiClient(ServerProperties.ScmProperties.GitHubProperties config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + config.getToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── Issues ────────────────────────────────────────────────────────────

    public IssueCreator.CreatedIssue createIssue(String owner, String repo,
                                                  String title, String body,
                                                  List<String> labels) {
        var payload = new HashMap<String, Object>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("labels", labels);

        var response = restClient.post()
                .uri("/repos/{owner}/{repo}/issues", owner, repo)
                .body(payload)
                .retrieve()
                .body(IssueResponse.class);

        return new IssueCreator.CreatedIssue(response.number(), response.htmlUrl());
    }

    // ── Source code ───────────────────────────────────────────────────────

    public String getFileContent(String owner, String repo, String path, String branch) {
        try {
            var response = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            owner, repo, path, branch)
                    .retrieve()
                    .body(ContentResponse.class);

            if (response == null || response.content() == null) return null;
            return new String(Base64.getMimeDecoder().decode(response.content()));
        } catch (Exception ex) {
            log.debug("[GitHub] Could not fetch {}/{}/{}: {}", owner, repo, path, ex.getMessage());
            return null;
        }
    }

    public String getFileSha(String owner, String repo, String path, String branch) {
        try {
            var response = restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            owner, repo, path, branch)
                    .retrieve()
                    .body(ContentResponse.class);
            return response != null ? response.sha() : null;
        } catch (Exception ex) {
            log.debug("[GitHub] Could not get SHA for {}/{}/{}: {}", owner, repo, path, ex.getMessage());
            return null;
        }
    }

    // ── Branches ──────────────────────────────────────────────────────────

    public String getBranchSha(String owner, String repo, String branch) {
        try {
            var response = restClient.get()
                    .uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", owner, repo, branch)
                    .retrieve()
                    .body(RefResponse.class);
            return response != null ? response.object().sha() : null;
        } catch (Exception ex) {
            log.warn("[GitHub] Could not get SHA for {}/{} branch {}: {}", owner, repo, branch, ex.getMessage());
            return null;
        }
    }

    public void createBranch(String owner, String repo, String branchName, String fromSha) {
        restClient.post()
                .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                .body(Map.of("ref", "refs/heads/" + branchName, "sha", fromSha))
                .retrieve()
                .toBodilessEntity();
    }

    // ── File commits ──────────────────────────────────────────────────────

    public void createOrUpdateFile(String owner, String repo,
                                   String path, String message, String content,
                                   String existingSha, String branch) {
        var payload = new HashMap<String, Object>();
        payload.put("message", message);
        payload.put("content", Base64.getEncoder().encodeToString(content.getBytes()));
        payload.put("branch", branch);
        if (existingSha != null) payload.put("sha", existingSha);

        restClient.put()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    // ── Pull requests ─────────────────────────────────────────────────────

    public String createPullRequest(String owner, String repo,
                                    String title, String body, String head, String base) {
        var response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                .body(Map.of("title", title, "body", body, "head", head, "base", base))
                .retrieve()
                .body(PrResponse.class);
        return response != null ? response.htmlUrl() : null;
    }

    // ── Wire DTOs ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssueResponse(int number, @JsonProperty("html_url") String htmlUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentResponse(String sha, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefResponse(RefObject object) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefObject(String sha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PrResponse(@JsonProperty("html_url") String htmlUrl) {}
}
