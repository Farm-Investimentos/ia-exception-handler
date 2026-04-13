package io.github.exceptionintelligence.sdk.model;

/**
 * A resolved stack frame sent to the server.
 * The {@code file} path is a SCM-relative path (e.g. {@code src/main/java/com/example/Service.java}).
 */
public record StackFrame(
        String file,
        String function,
        int line,
        Integer column,
        boolean isProjectCode
) {}
