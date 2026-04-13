package io.github.exceptionintelligence.server.config;

import io.github.exceptionintelligence.server.ai.LlmClient;
import io.github.exceptionintelligence.server.ai.LlmPromptBuilder;
import io.github.exceptionintelligence.server.ai.bedrock.BedrockLlmClient;
import io.github.exceptionintelligence.server.ai.claude.ClaudeLlmClient;
import io.github.exceptionintelligence.server.ai.gemini.GeminiLlmClient;
import io.github.exceptionintelligence.server.ai.openai.OpenAiLlmClient;
import io.github.exceptionintelligence.server.dedup.DeduplicationService;
import io.github.exceptionintelligence.server.notification.Notifier;
import io.github.exceptionintelligence.server.notification.slack.SlackNotifier;
import io.github.exceptionintelligence.server.notification.teams.TeamsNotifier;
import io.github.exceptionintelligence.server.pipeline.steps.*;
import io.github.exceptionintelligence.server.scm.IssueCreator;
import io.github.exceptionintelligence.server.scm.PrCreator;
import io.github.exceptionintelligence.server.scm.SourceCodeFetcher;
import io.github.exceptionintelligence.server.scm.github.GitHubApiClient;
import io.github.exceptionintelligence.server.scm.github.GitHubIssueCreator;
import io.github.exceptionintelligence.server.scm.github.GitHubPrCreator;
import io.github.exceptionintelligence.server.scm.github.GitHubSourceCodeFetcher;
import io.github.exceptionintelligence.server.scm.jira.JiraIssueCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Wires all server components: LLM, SCM, Notification and infrastructure beans.
 * Providers are selected via the {@code exception-intelligence.*.provider} properties
 * using factory methods rather than {@code @ConditionalOnProperty}, keeping the
 * configuration in one place and easy to trace.
 */
@Configuration
@EnableConfigurationProperties(ServerProperties.class)
public class ServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);

    // ── Async executor ────────────────────────────────────────────────────

    @Bean(name = "exceptionIntelligenceExecutor")
    public Executor exceptionIntelligenceExecutor(ServerProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(props.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(props.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix(props.getAsync().getThreadNamePrefix());
        executor.initialize();
        return executor;
    }

    // ── LLM ───────────────────────────────────────────────────────────────

    @Bean
    public LlmClient llmClient(ServerProperties props, LlmPromptBuilder promptBuilder) {
        String provider = props.getLlm().getProvider();
        log.info("[Config] LLM provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "bedrock" -> new BedrockLlmClient(props.getLlm().getBedrock(), promptBuilder);
            case "openai"  -> new OpenAiLlmClient(props.getLlm().getOpenai(), promptBuilder);
            case "gemini"  -> new GeminiLlmClient(props.getLlm().getGemini(), promptBuilder);
            default        -> new ClaudeLlmClient(props.getLlm().getClaude(), promptBuilder);
        };
    }

    // ── SCM (GitHub) ──────────────────────────────────────────────────────

    @Bean
    public GitHubApiClient gitHubApiClient(ServerProperties props) {
        return new GitHubApiClient(props.getScm().getGithub());
    }

    @Bean
    public SourceCodeFetcher sourceCodeFetcher(GitHubApiClient apiClient, ServerProperties props) {
        return new GitHubSourceCodeFetcher(
                apiClient,
                props.getScm().getGithub(),
                props.getScm().getGithub().getSourceContextLines()
        );
    }

    @Bean
    public IssueCreator issueCreator(GitHubApiClient apiClient, ServerProperties props) {
        String type = props.getIssueTracker().getType();
        log.info("[Config] Issue tracker: {}", type);
        return switch (type.toLowerCase()) {
            case "jira" -> new JiraIssueCreator(props.getIssueTracker().getJira());
            default     -> new GitHubIssueCreator(apiClient, props.getScm().getGithub());
        };
    }

    @Bean
    public PrCreator prCreator(GitHubApiClient apiClient, ServerProperties props) {
        return new GitHubPrCreator(apiClient, props.getScm().getGithub());
    }

    // ── Notification ──────────────────────────────────────────────────────

    @Bean
    public Notifier notifier(ServerProperties props) {
        String provider = props.getNotification().getProvider();
        log.info("[Config] Notification provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "slack" -> new SlackNotifier(props.getNotification().getSlack());
            default      -> new TeamsNotifier(props.getNotification().getTeams());
        };
    }

    // ── Deduplication ─────────────────────────────────────────────────────

    @Bean
    public DeduplicationService deduplicationService(ServerProperties props,
                                                      org.springframework.context.ApplicationContext ctx) {
        if ("redis".equalsIgnoreCase(props.getDeduplication().getStore())) {
            try {
                StringRedisTemplate redis = ctx.getBean(StringRedisTemplate.class);
                return new DeduplicationService(props.getDeduplication(), redis);
            } catch (Exception ex) {
                log.warn("[Config] Redis not available — falling back to memory dedup: {}", ex.getMessage());
            }
        }
        return new DeduplicationService(props.getDeduplication());
    }

    // ── Pipeline steps ────────────────────────────────────────────────────

    @Bean
    public DeduplicationStep deduplicationStep(DeduplicationService deduplicationService) {
        return new DeduplicationStep(deduplicationService);
    }

    @Bean
    public SourceCodeFetchStep sourceCodeFetchStep(SourceCodeFetcher sourceCodeFetcher) {
        return new SourceCodeFetchStep(sourceCodeFetcher);
    }

    @Bean
    public LlmAnalysisStep llmAnalysisStep(LlmClient llmClient) {
        return new LlmAnalysisStep(llmClient);
    }

    @Bean
    public IssueCreationStep issueCreationStep(IssueCreator issueCreator) {
        return new IssueCreationStep(issueCreator);
    }

    @Bean
    public PrCreationStep prCreationStep(PrCreator prCreator) {
        return new PrCreationStep(prCreator);
    }

    @Bean
    public NotificationStep notificationStep(Notifier notifier) {
        return new NotificationStep(notifier);
    }
}
