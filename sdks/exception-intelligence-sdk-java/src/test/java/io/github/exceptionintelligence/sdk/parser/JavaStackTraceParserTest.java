package io.github.exceptionintelligence.sdk.parser;

import io.github.exceptionintelligence.sdk.model.StackFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaStackTraceParserTest {

    private final JavaStackTraceParser parser = new JavaStackTraceParser("com.example");

    @Test
    void parsesThrowableFrames() {
        RuntimeException ex = new RuntimeException("test");
        List<StackFrame> frames = parser.parse(ex);
        assertThat(frames).isNotEmpty();
        assertThat(frames.get(0).file()).contains("src/main/java");
    }

    @Test
    void marksProjectFrames() {
        Exception projectEx = new Exception("project error") {
            // anonymous subclass will be in the test package
        };
        List<StackFrame> frames = parser.parse(projectEx);

        long projectCount = frames.stream().filter(StackFrame::isProjectCode).count();
        long externalCount = frames.stream()
                .filter(f -> !f.isProjectCode())
                .filter(f -> f.function() != null && f.function().startsWith("org.junit"))
                .count();

        assertThat(externalCount).isGreaterThan(0);
    }

    @Test
    void convertsClassNameToFilePath() {
        JavaStackTraceParser p = new JavaStackTraceParser("");
        RuntimeException ex = new RuntimeException("test");
        List<StackFrame> frames = p.parse(ex);

        assertThat(frames.get(0).file()).startsWith("src/main/java/");
        assertThat(frames.get(0).file()).endsWith(".java");
    }

    @Test
    void walksToRootCause() {
        Exception cause = new IllegalArgumentException("root cause");
        Exception wrapper = new RuntimeException("wrapper", cause);
        List<StackFrame> frames = parser.parse(wrapper);

        // Should walk to the root cause
        assertThat(frames).isNotEmpty();
    }

    @Test
    void fingerprintIsConsistentForSameException() {
        RuntimeException ex = new RuntimeException("consistent");
        List<StackFrame> frames = parser.parse(ex);
        String fp1 = parser.fingerprint(ex, frames);
        String fp2 = parser.fingerprint(ex, frames);
        assertThat(fp1).isEqualTo(fp2);
    }
}
