package io.github.exceptionintelligence.server.pipeline;

import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionPipelineTest {

    @Test
    void stepsExecuteInOrder() {
        var order = new java.util.ArrayList<Integer>();

        PipelineStep step1 = new PipelineStep() {
            public void execute(PipelineContext ctx) { order.add(10); }
            public int getOrder() { return 10; }
        };
        PipelineStep step2 = new PipelineStep() {
            public void execute(PipelineContext ctx) { order.add(20); }
            public int getOrder() { return 20; }
        };

        var pipeline = new ExceptionPipeline(List.of(step2, step1));
        pipeline.process(sampleReport());

        assertThat(order).containsExactly(10, 20);
    }

    @Test
    void abortedContextSkipsRemainingSteps() {
        var executed = new AtomicInteger(0);

        PipelineStep aborter = new PipelineStep() {
            public void execute(PipelineContext ctx) {
                executed.incrementAndGet();
                ctx.abort("duplicate");
            }
            public int getOrder() { return 10; }
        };
        PipelineStep shouldNotRun = new PipelineStep() {
            public void execute(PipelineContext ctx) { executed.incrementAndGet(); }
            public int getOrder() { return 20; }
        };

        new ExceptionPipeline(List.of(aborter, shouldNotRun)).process(sampleReport());

        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    void failingStepDoesNotAbortPipeline() {
        var executed = new AtomicInteger(0);

        PipelineStep failingStep = new PipelineStep() {
            public void execute(PipelineContext ctx) { throw new RuntimeException("step failed"); }
            public int getOrder() { return 10; }
        };
        PipelineStep nextStep = new PipelineStep() {
            public void execute(PipelineContext ctx) { executed.incrementAndGet(); }
            public int getOrder() { return 20; }
        };

        new ExceptionPipeline(List.of(failingStep, nextStep)).process(sampleReport());

        assertThat(executed.get()).isEqualTo(1);
    }

    private ExceptionReport sampleReport() {
        return new ExceptionReport(
                "java", "spring-boot", "test-service", "test",
                Instant.now(), "test-thread",
                new ExceptionReport.ExceptionInfo(
                        "java.lang.RuntimeException",
                        "test error",
                        "RuntimeException\n  at com.example.Service.method(Service.java:10)",
                        List.of(new StackFrame("src/main/java/com/example/Service.java",
                                "com.example.Service.method", 10, null, true))
                ),
                null,
                "abc12345"
        );
    }
}
