package io.github.exceptionintelligence.sdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exception-intelligence")
public class SdkProperties {

    /** Master switch. Default: true. */
    private boolean enabled = true;

    /** URL of the exception-intelligence-server (required). */
    private String serverUrl = "http://localhost:8090";

    /** Optional API key forwarded in the X-API-Key header if the server requires authentication. */
    private String apiKey = "";

    /**
     * Base package of this application used to mark project frames.
     * Example: "com.mycompany.myservice"
     */
    private String basePackage = "";

    /** Spring Boot / framework identifier forwarded to the server. */
    private String framework = "spring-boot";

    /** Service name forwarded to the server (defaults to spring.application.name if not set). */
    private String serviceName = "";

    /** Environment tag (e.g. production, staging). */
    private String environment = "";

    private RepositoryProperties repository = new RepositoryProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();
    private TransportProperties transport = new TransportProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public RepositoryProperties getRepository() { return repository; }
    public void setRepository(RepositoryProperties repository) { this.repository = repository; }

    public RateLimitProperties getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitProperties rateLimit) { this.rateLimit = rateLimit; }

    public TransportProperties getTransport() { return transport; }
    public void setTransport(TransportProperties transport) { this.transport = transport; }

    /**
     * GitHub repository where source code lives and where issues/PRs will be opened.
     * The server uses this to fetch the source file that caused the exception.
     */
    public static class RepositoryProperties {
        /** GitHub owner (user or organization). Example: "my-org" */
        private String owner = "";
        /** Repository name. Example: "my-service" */
        private String name = "";
        /** Base branch used for source fetching and PR base. Default: "main" */
        private String branch = "main";

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
    }

    public static class RateLimitProperties {
        private boolean enabled = true;
        private int maxEventsPerMinute = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxEventsPerMinute() { return maxEventsPerMinute; }
        public void setMaxEventsPerMinute(int maxEventsPerMinute) { this.maxEventsPerMinute = maxEventsPerMinute; }
    }

    public static class TransportProperties {
        /** http | sqs */
        private String mode = "http";
        private String sqsQueueUrl = "";

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getSqsQueueUrl() { return sqsQueueUrl; }
        public void setSqsQueueUrl(String sqsQueueUrl) { this.sqsQueueUrl = sqsQueueUrl; }
    }
}
