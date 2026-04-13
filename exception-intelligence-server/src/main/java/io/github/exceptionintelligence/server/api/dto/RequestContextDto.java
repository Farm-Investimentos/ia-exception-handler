package io.github.exceptionintelligence.server.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * HTTP / browser request context captured by the SDK at the moment the exception occurred.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RequestContextDto(
        String method,
        String uri,
        String queryString,
        Map<String, String> headers,
        String body,
        String authenticatedUser
) {}
