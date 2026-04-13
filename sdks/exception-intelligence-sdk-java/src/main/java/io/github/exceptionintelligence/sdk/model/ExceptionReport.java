package io.github.exceptionintelligence.sdk.model;

import java.time.Instant;
import java.util.List;

/**
 * Universal exception report payload sent to the server.
 * Matches the {@code ExceptionReportRequest} contract expected by the server API.
 */
public class ExceptionReport {

    private String language = "java";
    private String framework;
    private String serviceName;
    private String environment;
    private String timestamp;
    private String threadName;
    private ExceptionInfo exception;
    private RequestContext request;
    private String fingerprint;
    private RepositoryInfo repository;

    public ExceptionReport() {}

    public static Builder builder() { return new Builder(); }

    // ── Getters (for Jackson serialization) ───────────────────────────────

    public String getLanguage() { return language; }
    public String getFramework() { return framework; }
    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public String getTimestamp() { return timestamp; }
    public String getThreadName() { return threadName; }
    public ExceptionInfo getException() { return exception; }
    public RequestContext getRequest() { return request; }
    public String getFingerprint() { return fingerprint; }
    public RepositoryInfo getRepository() { return repository; }

    // ── Nested types ──────────────────────────────────────────────────────

    public static class RepositoryInfo {
        private String owner;
        private String name;
        private String branch;

        public RepositoryInfo(String owner, String name, String branch) {
            this.owner = owner;
            this.name = name;
            this.branch = branch;
        }

        public String getOwner() { return owner; }
        public String getName() { return name; }
        public String getBranch() { return branch; }
    }

    public static class ExceptionInfo {
        private String type;
        private String message;
        private String rawStackTrace;
        private List<StackFrame> frames;

        public ExceptionInfo(String type, String message, String rawStackTrace, List<StackFrame> frames) {
            this.type = type;
            this.message = message;
            this.rawStackTrace = rawStackTrace;
            this.frames = frames;
        }

        public String getType() { return type; }
        public String getMessage() { return message; }
        public String getRawStackTrace() { return rawStackTrace; }
        public List<StackFrame> getFrames() { return frames; }
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static class Builder {
        private final ExceptionReport report = new ExceptionReport();

        public Builder language(String language) { report.language = language; return this; }
        public Builder framework(String framework) { report.framework = framework; return this; }
        public Builder serviceName(String serviceName) { report.serviceName = serviceName; return this; }
        public Builder environment(String environment) { report.environment = environment; return this; }
        public Builder timestamp(Instant timestamp) {
            report.timestamp = timestamp.toString();
            return this;
        }
        public Builder threadName(String threadName) { report.threadName = threadName; return this; }
        public Builder exception(ExceptionInfo exception) { report.exception = exception; return this; }
        public Builder request(RequestContext request) { report.request = request; return this; }
        public Builder fingerprint(String fingerprint) { report.fingerprint = fingerprint; return this; }
        public Builder repository(RepositoryInfo repository) { report.repository = repository; return this; }

        public ExceptionReport build() { return report; }
    }
}
