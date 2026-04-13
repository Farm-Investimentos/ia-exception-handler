package io.github.exceptionintelligence.server.ai.bedrock;

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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.List;

/**
 * LLM client that routes requests through AWS Bedrock using the Converse API.
 * The Converse API provides a unified interface for all Bedrock models
 * (Claude, Titan, Llama, Mistral, etc.) — no model-specific payload format needed.
 *
 * Activated when {@code exception-intelligence.llm.provider=bedrock}.
 *
 * Credentials are resolved via:
 * 1. Explicit {@code access-key} / {@code secret-key} in config (useful for local dev).
 * 2. Standard AWS credential chain: env vars → system props → IAM role → ~/.aws/credentials.
 */
public class BedrockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockLlmClient.class);

    private final BedrockRuntimeClient bedrockClient;
    private final ServerProperties.LlmProperties.BedrockProperties config;
    private final LlmPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public BedrockLlmClient(ServerProperties.LlmProperties.BedrockProperties config,
                             LlmPromptBuilder promptBuilder) {
        this.config = config;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper();

        var credentialsProvider = (config.getAccessKey() != null && !config.getAccessKey().isBlank())
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()))
                : DefaultCredentialsProvider.create();

        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Override
    public AnalysisResult analyze(LlmRequest request) {
        String system = promptBuilder.buildSystemPrompt(
                request.report().language(), request.report().framework());
        String userMessage = promptBuilder.buildUserMessage(request);

        try {
            var converseRequest = ConverseRequest.builder()
                    .modelId(config.getModelId())
                    .system(SystemContentBlock.fromText(system))
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(userMessage))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(config.getMaxTokens())
                            .temperature(0.0f)
                            .build())
                    .build();

            ConverseResponse response = bedrockClient.converse(converseRequest);

            if (response.output() == null || response.output().message() == null) {
                return AnalysisResult.unavailable();
            }

            List<ContentBlock> content = response.output().message().content();
            if (content == null || content.isEmpty()) {
                return AnalysisResult.unavailable();
            }

            return parseAnalysisJson(content.get(0).text());

        } catch (Exception ex) {
            log.warn("[Bedrock] Converse API failed for modelId={}: {}", config.getModelId(), ex.getMessage());
            return AnalysisResult.unavailable();
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
            log.warn("[Bedrock] Failed to parse analysis JSON: {}", ex.getMessage());
            return AnalysisResult.builder()
                    .problemDescription(text.length() > 2000 ? text.substring(0, 2000) + "..." : text)
                    .build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalysisDto(
            @JsonProperty("problemDescription") String problemDescription,
            @JsonProperty("suggestedFix") String suggestedFix,
            @JsonProperty("fixedSourceCode") String fixedSourceCode
    ) {}
}
