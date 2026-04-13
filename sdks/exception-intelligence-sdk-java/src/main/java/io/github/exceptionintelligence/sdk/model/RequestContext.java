package io.github.exceptionintelligence.sdk.model;

import java.util.Map;

/**
 * HTTP request context captured at the moment the exception occurred.
 */
public record RequestContext(
        String method,
        String uri,
        String queryString,
        Map<String, String> headers,
        String body,
        String authenticatedUser
) {}
