package io.github.exceptionintelligence.server.ai.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.exceptionintelligence.server.ai.LlmClient;
import io.github.exceptionintelligence.server.ai.LlmPromptBuilder;
import io.github.exceptionintelligence.server.ai.LlmRequest;
import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * LLM client that calls the Anthropic Claude API directly.
 * Activated when {@code exception-intelligence.llm.provider=claude}.
 */
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);
    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ServerProperties.LlmProperties.ClaudeProperties config;
    private final LlmPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ClaudeLlmClient(ServerProperties.LlmProperties.ClaudeProperties config,
                           LlmPromptBuilder promptBuilder) {
        this.config = config;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper();

        var factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = config.getTimeoutSeconds() * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("x-api-key", config.getApiKey())
                .defaultHeader("anthropic-version", API_VERSION)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public AnalysisResult analyze(LlmRequest request) {
        String system = promptBuilder.buildSystemPrompt(
                request.report().language(), request.report().framework());
        String user = promptBuilder.buildUserMessage(request);

        var body = Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user))
        );

        try {
            var response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(ClaudeResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                return AnalysisResult.unavailable();
            }
            return parseResponse(response.content().get(0).text());
        } catch (RestClientException ex) {
            log.warn("[Claude] API call failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.warn("[Claude] Unexpected error: {}", ex.getMessage());
            throw new RestClientException("Unexpected error calling Claude API: " + ex.getMessage(), ex);
        }
    }

    private AnalysisResult parseResponse(String text) {
        try {
            String json = text.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            var dto = objectMapper.readValue(json, AnalysisDto.class);
            return AnalysisResult.builder()
                    .problemDescription(dto.problemDescription())
                    .suggestedFix(dto.suggestedFix())
                    .fixedSourceCode(dto.fixedSourceCode())
                    .build();
        } catch (Exception ex) {
            log.warn("[Claude] Failed to parse JSON response: {}", ex.getMessage());
            return AnalysisResult.builder()
                    .problemDescription(text.length() > 2000 ? text.substring(0, 2000) + "..." : text)
                    .build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClaudeResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalysisDto(String problemDescription, String suggestedFix, String fixedSourceCode) {}
}
