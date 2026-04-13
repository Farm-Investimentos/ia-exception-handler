package io.github.exceptionintelligence.server.model;

import java.util.Optional;

/**
 * Result of LLM analysis for an exception report.
 */
public class AnalysisResult {

    private final String problemDescription;
    private final String suggestedFix;
    private final String fixedSourceCode;

    private AnalysisResult(Builder builder) {
        this.problemDescription = builder.problemDescription;
        this.suggestedFix = builder.suggestedFix;
        this.fixedSourceCode = builder.fixedSourceCode;
    }

    public static AnalysisResult unavailable() {
        return new Builder()
                .problemDescription("Analysis unavailable — LLM could not be reached or returned an invalid response.")
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public String getProblemDescription() { return problemDescription; }
    public Optional<String> getSuggestedFix() { return Optional.ofNullable(suggestedFix); }
    public Optional<String> getFixedSourceCode() { return Optional.ofNullable(fixedSourceCode); }

    public boolean hasFix() {
        return fixedSourceCode != null && !fixedSourceCode.isBlank();
    }

    public static class Builder {
        private String problemDescription;
        private String suggestedFix;
        private String fixedSourceCode;

        public Builder problemDescription(String v) { problemDescription = v; return this; }
        public Builder suggestedFix(String v) { suggestedFix = v; return this; }
        public Builder fixedSourceCode(String v) { fixedSourceCode = v; return this; }
        public AnalysisResult build() { return new AnalysisResult(this); }
    }
}
