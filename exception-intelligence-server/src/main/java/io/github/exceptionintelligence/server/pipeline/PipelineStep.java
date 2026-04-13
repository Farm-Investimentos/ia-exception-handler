package io.github.exceptionintelligence.server.pipeline;

/**
 * A single step in the exception analysis pipeline.
 * Steps are executed in ascending {@link #getOrder()} value.
 * If a step calls {@link PipelineContext#abort(String)}, subsequent steps are skipped.
 */
public interface PipelineStep {

    void execute(PipelineContext context);

    int getOrder();
}
