package io.github.exceptionintelligence.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionReportTest {

    @Test
    void topProjectFrameReturnsFirstMarkedFrame() {
        var report = reportWith(
                new StackFrame("node_modules/express/index.js", "express.handle", 100, null, false),
                new StackFrame("src/controllers/order.js", "OrderController.create", 42, null, true)
        );
        assertThat(report.topProjectFrame()).isNotNull();
        assertThat(report.topProjectFrame().file()).isEqualTo("src/controllers/order.js");
    }

    @Test
    void topProjectFrameFallsBackToFirstFrameWhenNoneMarked() {
        var report = reportWith(
                new StackFrame("node_modules/lib.js", "lib.run", 1, null, false)
        );
        assertThat(report.topProjectFrame()).isNotNull();
        assertThat(report.topProjectFrame().file()).isEqualTo("node_modules/lib.js");
    }

    @Test
    void effectiveFingerprintUsesPrecomputedIfPresent() {
        var report = reportWith(List.of(), "my-fp-abc");
        assertThat(report.effectiveFingerprint()).isEqualTo("my-fp-abc");
    }

    @Test
    void effectiveFingerprintComputedWhenAbsent() {
        var report = reportWith(
                List.of(new StackFrame("src/Foo.java", "Foo.bar", 10, null, true)),
                null
        );
        assertThat(report.effectiveFingerprint()).isNotBlank();
    }

    @Test
    void simpleTypeExtractedFromFullyQualified() {
        var report = reportWith(List.of(), null);
        assertThat(report.exception().simpleType()).isEqualTo("NullPointerException");
    }

    private ExceptionReport reportWith(StackFrame... frames) {
        return reportWith(List.of(frames), null);
    }

    private ExceptionReport reportWith(List<StackFrame> frames, String fingerprint) {
        return new ExceptionReport(
                "java", "spring-boot", "svc", "prod",
                Instant.now(), "main",
                new ExceptionReport.ExceptionInfo(
                        "java.lang.NullPointerException", "NPE", null, frames),
                null,
                fingerprint
        );
    }
}
