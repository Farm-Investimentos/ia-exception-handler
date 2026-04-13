package io.github.exceptionintelligence.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "exception-intelligence")
public class ServerProperties {

    private LlmProperties llm = new LlmProperties();
    private ScmProperties scm = new ScmProperties();
    private IssueTrackerProperties issueTracker = new IssueTrackerProperties();
    private NotificationProperties notification = new NotificationProperties();
    private DeduplicationProperties deduplication = new DeduplicationProperties();
    private SqsProperties sqs = new SqsProperties();
    private AsyncProperties async = new AsyncProperties();

    /**
     * Idioma das respostas geradas pelo LLM (análise, título da issue, notificações).
     * Use qualquer tag BCP-47, ex: "pt-BR", "en-US", "es-ES".
     * Padrão: pt-BR.
     */
    private String responseLanguage = "pt-BR";

    public String getResponseLanguage() { return responseLanguage; }
    public void setResponseLanguage(String responseLanguage) { this.responseLanguage = responseLanguage; }

    public LlmProperties getLlm() { return llm; }
    public void setLlm(LlmProperties llm) { this.llm = llm; }

    public ScmProperties getScm() { return scm; }
    public void setScm(ScmProperties scm) { this.scm = scm; }

    public IssueTrackerProperties getIssueTracker() { return issueTracker; }
    public void setIssueTracker(IssueTrackerProperties issueTracker) { this.issueTracker = issueTracker; }

    public NotificationProperties getNotification() { return notification; }
    public void setNotification(NotificationProperties notification) { this.notification = notification; }

    public DeduplicationProperties getDeduplication() { return deduplication; }
    public void setDeduplication(DeduplicationProperties deduplication) { this.deduplication = deduplication; }

    public SqsProperties getSqs() { return sqs; }
    public void setSqs(SqsProperties sqs) { this.sqs = sqs; }

    public AsyncProperties getAsync() { return async; }
    public void setAsync(AsyncProperties async) { this.async = async; }

    // ── LLM ───────────────────────────────────────────────────────────────

    public static class LlmProperties {
        /** Active provider: claude | bedrock | openai | gemini */
        private String provider = "claude";
        private ClaudeProperties claude = new ClaudeProperties();
        private BedrockProperties bedrock = new BedrockProperties();
        private OpenAiProperties openai = new OpenAiProperties();
        private GeminiProperties gemini = new GeminiProperties();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public ClaudeProperties getClaude() { return claude; }
        public void setClaude(ClaudeProperties claude) { this.claude = claude; }
        public BedrockProperties getBedrock() { return bedrock; }
        public void setBedrock(BedrockProperties bedrock) { this.bedrock = bedrock; }
        public OpenAiProperties getOpenai() { return openai; }
        public void setOpenai(OpenAiProperties openai) { this.openai = openai; }
        public GeminiProperties getGemini() { return gemini; }
        public void setGemini(GeminiProperties gemini) { this.gemini = gemini; }

        public static class ClaudeProperties {
            private String apiKey = "";
            private String model = "claude-sonnet-4-6";
            private int maxTokens = 2048;
            private int timeoutSeconds = 30;

            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }

        public static class BedrockProperties {
            private String region = "us-east-1";
            private String modelId = "anthropic.claude-3-5-sonnet-20241022-v2:0";
            /** AWS Access Key — leave blank to use the default credential chain (IAM role, env vars, etc.). */
            private String accessKey = "";
            private String secretKey = "";
            private int maxTokens = 2048;

            public String getRegion() { return region; }
            public void setRegion(String region) { this.region = region; }
            public String getModelId() { return modelId; }
            public void setModelId(String modelId) { this.modelId = modelId; }
            public String getAccessKey() { return accessKey; }
            public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
            public String getSecretKey() { return secretKey; }
            public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        }

        public static class OpenAiProperties {
            private String apiKey = "";
            private String model = "gpt-4o";
            private int maxTokens = 2048;
            private int timeoutSeconds = 30;

            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }

        public static class GeminiProperties {
            private String apiKey = "";
            private String model = "gemini-1.5-pro";
            private int maxTokens = 2048;
            private int timeoutSeconds = 30;

            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            public int getTimeoutSeconds() { return timeoutSeconds; }
            public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        }
    }

    // ── SCM ───────────────────────────────────────────────────────────────

    public static class ScmProperties {
        /** Active provider: github | gitlab */
        private String provider = "github";
        private GitHubProperties github = new GitHubProperties();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public GitHubProperties getGithub() { return github; }
        public void setGithub(GitHubProperties github) { this.github = github; }

        public static class GitHubProperties {
            private String token = "";
            private String owner = "";
            private String repo = "";
            private String baseBranch = "main";
            private List<String> labels = List.of("bug", "auto-detected");
            private int sourceContextLines = 50;
            private IssueProperties issue = new IssueProperties();
            private PullRequestProperties pullRequest = new PullRequestProperties();

            public String getToken() { return token; }
            public void setToken(String token) { this.token = token; }
            public String getOwner() { return owner; }
            public void setOwner(String owner) { this.owner = owner; }
            public String getRepo() { return repo; }
            public void setRepo(String repo) { this.repo = repo; }
            public String getBaseBranch() { return baseBranch; }
            public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
            public List<String> getLabels() { return labels; }
            public void setLabels(List<String> labels) { this.labels = labels; }
            public int getSourceContextLines() { return sourceContextLines; }
            public void setSourceContextLines(int sourceContextLines) { this.sourceContextLines = sourceContextLines; }
            public IssueProperties getIssue() { return issue; }
            public void setIssue(IssueProperties issue) { this.issue = issue; }
            public PullRequestProperties getPullRequest() { return pullRequest; }
            public void setPullRequest(PullRequestProperties pullRequest) { this.pullRequest = pullRequest; }

            public static class IssueProperties {
                private boolean enabled = true;
                private String titlePrefix = "[AUTO]";

                public boolean isEnabled() { return enabled; }
                public void setEnabled(boolean enabled) { this.enabled = enabled; }
                public String getTitlePrefix() { return titlePrefix; }
                public void setTitlePrefix(String titlePrefix) { this.titlePrefix = titlePrefix; }
            }

            public static class PullRequestProperties {
                private boolean enabled = true;
                private String commitMessage = "fix: auto-suggested fix for %s";

                public boolean isEnabled() { return enabled; }
                public void setEnabled(boolean enabled) { this.enabled = enabled; }
                public String getCommitMessage() { return commitMessage; }
                public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }
            }
        }
    }

    // ── Issue Tracker ─────────────────────────────────────────────────────

    public static class IssueTrackerProperties {
        /** Which issue tracker to use. Values: github (default), jira. */
        private String type = "github";
        private JiraProperties jira = new JiraProperties();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public JiraProperties getJira() { return jira; }
        public void setJira(JiraProperties jira) { this.jira = jira; }

        public static class JiraProperties {
            /** Jira Cloud base URL, e.g. "https://mycompany.atlassian.net" */
            private String url = "";
            /** Jira user e-mail */
            private String username = "";
            /** Jira API token (https://id.atlassian.com/manage-profile/security/api-tokens) */
            private String apiToken = "";
            /** Project key, e.g. "PROJ" */
            private String projectKey = "";
            /** Issue type name, e.g. "Bug" */
            private String issueType = "Bug";
            private java.util.List<String> labels = java.util.List.of("auto-detected");

            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }
            public String getApiToken() { return apiToken; }
            public void setApiToken(String apiToken) { this.apiToken = apiToken; }
            public String getProjectKey() { return projectKey; }
            public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
            public String getIssueType() { return issueType; }
            public void setIssueType(String issueType) { this.issueType = issueType; }
            public java.util.List<String> getLabels() { return labels; }
            public void setLabels(java.util.List<String> labels) { this.labels = labels; }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    public static class NotificationProperties {
        /** Active provider: teams | slack */
        private String provider = "teams";
        private TeamsProperties teams = new TeamsProperties();
        private SlackProperties slack = new SlackProperties();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public TeamsProperties getTeams() { return teams; }
        public void setTeams(TeamsProperties teams) { this.teams = teams; }
        public SlackProperties getSlack() { return slack; }
        public void setSlack(SlackProperties slack) { this.slack = slack; }

        public static class TeamsProperties {
            private boolean enabled = true;
            private String webhookUrl = "";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getWebhookUrl() { return webhookUrl; }
            public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        }

        public static class SlackProperties {
            private boolean enabled = false;
            private String webhookUrl = "";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getWebhookUrl() { return webhookUrl; }
            public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        }
    }

    // ── Deduplication ─────────────────────────────────────────────────────

    public static class DeduplicationProperties {
        private boolean enabled = true;
        private int ttlMinutes = 60;
        /** memory | redis */
        private String store = "memory";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
        public String getStore() { return store; }
        public void setStore(String store) { this.store = store; }
    }

    // ── SQS ───────────────────────────────────────────────────────────────

    public static class SqsProperties {
        private boolean enabled = false;
        private String queueUrl = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getQueueUrl() { return queueUrl; }
        public void setQueueUrl(String queueUrl) { this.queueUrl = queueUrl; }
    }

    // ── Async ─────────────────────────────────────────────────────────────

    public static class AsyncProperties {
        private int corePoolSize = 2;
        private int maxPoolSize = 8;
        private int queueCapacity = 500;
        private String threadNamePrefix = "exc-intel-";

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }
}
