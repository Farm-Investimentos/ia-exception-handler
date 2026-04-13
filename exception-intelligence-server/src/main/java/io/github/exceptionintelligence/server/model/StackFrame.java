package io.github.exceptionintelligence.server.model;

/**
 * Represents a single resolved stack frame, language-agnostic.
 * The {@code file} path is a SCM-relative path already resolved by the SDK.
 */
public record StackFrame(
        String file,
        String function,
        int line,
        Integer column,
        boolean isProjectCode
) {
    @Override
    public String toString() {
        return function + " (" + file + ":" + line + ")";
    }
}
