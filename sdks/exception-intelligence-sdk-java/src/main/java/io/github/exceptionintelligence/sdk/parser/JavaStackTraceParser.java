package io.github.exceptionintelligence.sdk.parser;

import io.github.exceptionintelligence.sdk.model.StackFrame;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a Java {@link Throwable} to the SDK's {@link StackFrame} list.
 * <p>
 * Walks to the root cause, identifies project frames by {@code basePackage},
 * and resolves each class name to a SCM-compatible file path:
 * {@code com.example.Service} → {@code src/main/java/com/example/Service.java}.
 */
public class JavaStackTraceParser {

    private final String basePackage;

    public JavaStackTraceParser(String basePackage) {
        this.basePackage = basePackage != null ? basePackage : "";
    }

    public List<StackFrame> parse(Throwable throwable) {
        Throwable root = rootCause(throwable);
        StackTraceElement[] elements = root.getStackTrace();

        List<StackFrame> frames = new ArrayList<>();
        for (StackTraceElement el : elements) {
            frames.add(new StackFrame(
                    toFilePath(el.getClassName()),
                    el.getClassName() + "." + el.getMethodName(),
                    el.getLineNumber(),
                    null,
                    isProjectClass(el.getClassName())
            ));
        }
        return frames;
    }

    public String rawStackTrace(Throwable throwable) {
        var sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public String fingerprint(Throwable throwable, List<StackFrame> frames) {
        StackFrame top = frames.stream()
                .filter(StackFrame::isProjectCode)
                .findFirst()
                .orElse(frames.isEmpty() ? null : frames.get(0));

        String key = throwable.getClass().getName()
                + (top != null ? top.function() + top.line() : "");
        return Integer.toHexString(key.hashCode() & 0x7FFFFFFF);
    }

    private boolean isProjectClass(String className) {
        if (basePackage.isBlank()) return true;
        return className.startsWith(basePackage);
    }

    /**
     * Converts a fully-qualified Java class name to a SCM-relative source file path.
     * Inner classes ({@code Outer$Inner}) are reduced to the outer class file.
     */
    private String toFilePath(String className) {
        String path = className.replace('.', '/');
        int dollar = path.indexOf('$');
        if (dollar > 0) path = path.substring(0, dollar);
        return "src/main/java/" + path + ".java";
    }

    private Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
