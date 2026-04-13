package io.github.exceptionintelligence.server.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a single stack frame resolved by the SDK.
 * <p>
 * The {@code file} path is already resolved to a SCM-compatible relative path
 * by the SDK (e.g. {@code src/main/java/com/example/Service.java} for Java,
 * {@code src/services/order.service.ts} for Node.js).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StackFrameDto(
        @NotBlank String file,
        String function,
        int line,
        Integer column,
        boolean isProjectCode
) {}
