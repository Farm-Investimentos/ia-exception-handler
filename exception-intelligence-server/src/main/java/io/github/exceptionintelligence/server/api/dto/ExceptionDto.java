package io.github.exceptionintelligence.server.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Exception payload resolved by the SDK before sending to the server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExceptionDto(
        @NotBlank String type,
        String message,
        String rawStackTrace,
        @NotNull @Valid List<StackFrameDto> frames
) {}
