package io.github.exceptionintelligence.server.model;

import java.time.Instant;
import java.util.List;

/**
 * Internal, language-agnostic representation of an exception report.
 * Created from {@link io.github.exceptionintelligence.server.api.dto.ExceptionReportRequest}
 * and passed through the analysis pipeline.
 */
public record ExceptionReport(
        String language,
        String framework,
        String serviceName,
        String environment,
        Instant timestamp,
        String threadName,
        ExceptionInfo exception,
        RequestContextModel request,
        String fingerprint,
        RepositoryInfo repository
) {

    /**
     * GitHub repository that owns the source code for this exception.
     * Sent by the SDK — used by the server to fetch source, open issues and PRs
     * in the correct repo, even when multiple services report to the same server.
     */
    public record RepositoryInfo(String owner, String name, String branch) {
        public String effectiveBranch() {
            return (branch != null && !branch.isBlank()) ? branch : "main";
        }
    }


    /**
     * Exception metadata resolved by the SDK (type, message, raw stack, parsed frames).
     */
    public record ExceptionInfo(
            String type,
            String message,
            String rawStackTrace,
            List<StackFrame> frames
    ) {
        /** Simple name extracted from the fully-qualified type (e.g. NullPointerException). */
        public String simpleType() {
            if (type == null || type.isBlank()) return "UnknownException";
            int dot = type.lastIndexOf('.');
            return dot >= 0 ? type.substring(dot + 1) : type;
        }
    }

    /**
     * Returns the pre-computed fingerprint or derives one from exception type + top frame.
     */
    public String effectiveFingerprint() {
        if (fingerprint != null && !fingerprint.isBlank()) return fingerprint;
        StackFrame top = topProjectFrame();
        if (top != null) {
            return Integer.toHexString(
                    (exception.type() + top.function() + top.line()).hashCode() & 0x7FFFFFFF
            );
        }
        return Integer.toHexString(exception.type().hashCode() & 0x7FFFFFFF);
    }

    /** First stack frame marked as project code, or null if none. */
    public StackFrame topProjectFrame() {
        if (exception == null || exception.frames() == null) return null;
        return exception.frames().stream()
                .filter(StackFrame::isProjectCode)
                .findFirst()
                .orElse(exception.frames().isEmpty() ? null : exception.frames().get(0));
    }

    /** All frames marked as project code. Falls back to all frames if none are marked. */
    public List<StackFrame> projectFrames() {
        if (exception == null || exception.frames() == null) return List.of();
        List<StackFrame> project = exception.frames().stream()
                .filter(StackFrame::isProjectCode)
                .toList();
        return project.isEmpty() ? exception.frames() : project;
    }
}
