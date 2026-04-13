package io.github.exceptionintelligence.server.ai;

import io.github.exceptionintelligence.server.model.AnalysisResult;

/**
 * Strategy interface for LLM providers.
 * Implementations: {@code ClaudeLlmClient}, {@code BedrockLlmClient}, {@code OpenAiLlmClient}.
 */
public interface LlmClient {

    /**
     * Analyzes an exception using the configured LLM provider.
     *
     * @param request context assembled by the pipeline (exception, frames, source code, HTTP context)
     * @return analysis result — never null; use {@link AnalysisResult#unavailable()} on failure
     */
    AnalysisResult analyze(LlmRequest request);
}
