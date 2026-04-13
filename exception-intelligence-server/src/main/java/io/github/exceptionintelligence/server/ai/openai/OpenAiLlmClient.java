package io.github.exceptionintelligence.server.ai.openai;

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
 * LLM client that calls the OpenAI Chat Completions API.
 * Activated when {@code exception-intelligence.llm.provider=openai}.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final String BASE_URL = "https://api.openai.com";

    private final RestClient restClient;
    private final ServerProperties.LlmProperties.OpenAiProperties config;
    private final LlmPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(ServerProperties.LlmProperties.OpenAiProperties config,
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
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
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
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                )
        );

        try {
            var response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(OpenAiResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return AnalysisResult.unavailable();
            }
            String text = response.choices().get(0).message().content();
            return parseResponse(text);
        } catch (RestClientException ex) {
            log.warn("[OpenAI] API call failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.warn("[OpenAI] Unexpected error: {}", ex.getMessage());
            throw new RestClientException("Unexpected error calling OpenAI API: " + ex.getMessage(), ex);
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
            log.warn("[OpenAI] Failed to parse JSON response: {}", ex.getMessage());
            return AnalysisResult.builder()
                    .problemDescription(text.length() > 2000 ? text.substring(0, 2000) + "..." : text)
                    .build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalysisDto(String problemDescription, String suggestedFix, String fixedSourceCode) {}
}
