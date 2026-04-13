package io.github.exceptionintelligence.server.ai;

import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.StackFrame;

import java.util.List;

/**
 * Input to the LLM analysis step.
 * Groups all context needed to build the LLM prompt.
 */
public record LlmRequest(
        ExceptionReport report,
        List<StackFrame> projectFrames,
        String sourceCode,
        int sourceCodeStartLine
) {}
