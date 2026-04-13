package io.github.exceptionintelligence.server.scm.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.exceptionintelligence.server.config.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class JiraApiClient {

    private static final Logger log = LoggerFactory.getLogger(JiraApiClient.class);

    private final RestTemplate restTemplate;
    private final HttpHeaders defaultHeaders;
    private final ServerProperties.IssueTrackerProperties.JiraProperties config;

    public JiraApiClient(ServerProperties.IssueTrackerProperties.JiraProperties config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        String credentials = config.getUsername() + ":" + config.getApiToken();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        this.defaultHeaders = new HttpHeaders();
        defaultHeaders.set("Authorization", basicAuth);
        defaultHeaders.set("Content-Type", "application/json");
        defaultHeaders.set("Accept", "application/json");
    }

    public static final class CreatedIssue {
        private final String key;
        private final String url;

        public CreatedIssue(String key, String url) {
            this.key = key;
            this.url = url;
        }

        public String key() { return key; }
        public String url() { return url; }
    }

    public CreatedIssue createIssue(String summary, Map<String, Object> description) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", config.getProjectKey()));
        fields.put("summary", summary);
        fields.put("description", description);
        fields.put("issuetype", Map.of("name", config.getIssueType()));
        if (config.getLabels() != null && !config.getLabels().isEmpty()) {
            fields.put("labels", config.getLabels());
        }
        // Assignee: usa o próprio usuário autenticado se nenhum for configurado,
        // evitando erro "default assignee has no permission".
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            fields.put("assignee", Map.of("accountId", resolveAccountId()));
        }

        Map<String, Object> body = Map.of("fields", fields);
        String url = config.getUrl() + "/rest/api/3/issue";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, defaultHeaders);

        ResponseEntity<JiraCreateResponse> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, JiraCreateResponse.class);
        JiraCreateResponse r = resp.getBody();
        if (r == null || r.key == null) {
            throw new IllegalStateException("Jira retornou resposta inválida ao criar issue");
        }

        String issueUrl = config.getUrl() + "/browse/" + r.key;
        log.debug("[Jira] Issue criada: {}", issueUrl);
        return new CreatedIssue(r.key, issueUrl);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JiraCreateResponse {
        public String id;
        public String key;
        public String self;
    }

    private String resolveAccountId() {
        try {
            String url = config.getUrl() + "/rest/api/3/myself";
            HttpEntity<Void> entity = new HttpEntity<>(defaultHeaders);
            ResponseEntity<JiraMyselfResponse> resp = restTemplate.exchange(
                    url, HttpMethod.GET, entity, JiraMyselfResponse.class);
            if (resp.getBody() != null && resp.getBody().accountId != null) {
                return resp.getBody().accountId;
            }
        } catch (Exception e) {
            log.warn("[Jira] Não foi possível resolver accountId do usuário autenticado: {}", e.getMessage());
        }
        return config.getUsername(); // fallback
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JiraMyselfResponse {
        public String accountId;
        public String displayName;
    }
}
