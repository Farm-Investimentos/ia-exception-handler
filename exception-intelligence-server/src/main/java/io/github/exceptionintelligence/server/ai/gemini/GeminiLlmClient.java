package io.github.exceptionintelligence.server.ai.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.exceptionintelligence.server.ai.LlmClient;
import io.github.exceptionintelligence.server.ai.LlmPromptBuilder;
import io.github.exceptionintelligence.server.ai.LlmRequest;
import io.github.exceptionintelligence.server.config.ServerProperties;
import io.github.exceptionintelligence.server.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * LLM client for Google Gemini API.
 * Activated when {@code exception-intelligence.llm.provider=gemini}.
 */
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestTemplate restTemplate;
    private final ServerProperties.LlmProperties.GeminiProperties config;
    private final LlmPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(ServerProperties.LlmProperties.GeminiProperties config,
                            LlmPromptBuilder promptBuilder) {
        this.config = config;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        rf.setReadTimeout(config.getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(rf);
    }

    @Override
    @Retryable(value = RestClientException.class, exclude = HttpClientErrorException.class,
               maxAttempts = 2, backoff = @Backoff(delay = 1000, multiplier = 2))
    public AnalysisResult analyze(LlmRequest request) {
        String system = promptBuilder.buildSystemPrompt(
                request.report().language(), request.report().framework());
        String userMessage = promptBuilder.buildUserMessage(request);

        Map<String, Object> reqBody = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                )),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", system))
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", config.getMaxTokens(),
                        "temperature", 0.0
                )
        );

        String uri = BASE_URL + "/v1beta/models/" + config.getModel()
                + ":generateContent?key=" + config.getApiKey();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);
            ResponseEntity<GeminiResponse> resp = restTemplate.exchange(
                    uri, HttpMethod.POST, entity, GeminiResponse.class);

            GeminiResponse body = resp.getBody();
            if (body == null || body.candidates == null || body.candidates.isEmpty()) {
                return AnalysisResult.unavailable();
            }
            List<GeminiPart> parts = body.candidates.get(0).content.parts;
            if (parts == null || parts.isEmpty()) {
                return AnalysisResult.unavailable();
            }
            return parseAnalysisJson(parts.get(0).text);

        } catch (HttpClientErrorException ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody.contains("API_KEY_INVALID")) {
                log.warn("[Gemini] API key inválida.");
            } else if (responseBody.contains("RESOURCE_EXHAUSTED")) {
                log.warn("[Gemini] Quota excedida.");
            } else {
                log.warn("[Gemini] API error ({}): {}", ex.getStatusCode(), responseBody);
            }
            throw ex;
        } catch (Exception ex) {
            log.warn("[Gemini] API call failed: {}", ex.getMessage());
            throw new RestClientException("Gemini API failed: " + ex.getMessage(), ex);
        }
    }

    private AnalysisResult parseAnalysisJson(String text) {
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
            log.warn("[Gemini] Failed to parse JSON response: {}", ex.getMessage());
            return AnalysisResult.builder()
                    .problemDescription(text.length() > 2000 ? text.substring(0, 2000) + "..." : text)
                    .build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiResponse {
        public List<GeminiCandidate> candidates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiCandidate {
        public GeminiContent content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiContent {
        public List<GeminiPart> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiPart {
        public String text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalysisDto(
            @JsonProperty("problemDescription") String problemDescription,
            @JsonProperty("suggestedFix") String suggestedFix,
            @JsonProperty("fixedSourceCode") String fixedSourceCode
    ) {}
}
