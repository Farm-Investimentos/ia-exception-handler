package io.github.exceptionintelligence.server.model;

import java.util.Map;

/**
 * HTTP / browser request context at the moment the exception occurred.
 */
public record RequestContextModel(
        String method,
        String uri,
        String queryString,
        Map<String, String> headers,
        String body,
        String authenticatedUser
) {
    @Override
    public String toString() {
        return (method != null ? method : "?") + " " + (uri != null ? uri : "?");
    }
}
