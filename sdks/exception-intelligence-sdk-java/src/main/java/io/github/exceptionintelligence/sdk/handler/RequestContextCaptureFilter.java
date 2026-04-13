package io.github.exceptionintelligence.sdk.handler;

import io.github.exceptionintelligence.sdk.model.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servlet filter that captures HTTP request context and stores it in a ThreadLocal
 * before the request is processed. Retrieved by {@link GlobalExceptionInterceptor}
 * if an exception occurs during the request.
 */
public class RequestContextCaptureFilter implements Filter {

    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (!(req instanceof HttpServletRequest httpReq)) {
            chain.doFilter(req, res);
            return;
        }

        var wrapped = new ContentCachingRequestWrapper(httpReq, MAX_BODY_BYTES);

        try {
            RequestContext ctx = buildContext(wrapped);
            RequestContextHolder.set(ctx);
            chain.doFilter(wrapped, res);

            // Enrich with body that becomes available after doFilter
            String body = readBody(wrapped);
            if (body != null && !body.isBlank()) {
                RequestContextHolder.set(new RequestContext(
                        ctx.method(), ctx.uri(), ctx.queryString(),
                        ctx.headers(), body, ctx.authenticatedUser()
                ));
            }
        } finally {
            RequestContextHolder.clear();
        }
    }

    private RequestContext buildContext(HttpServletRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement().toLowerCase();
                headers.put(name, SENSITIVE_HEADERS.contains(name)
                        ? "[REDACTED]" : req.getHeader(name));
            }
        }

        Map<String, String> params = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> params.put(k, String.join(",", v)));

        String user = resolveUser(req);

        return new RequestContext(
                req.getMethod(),
                req.getRequestURI(),
                req.getQueryString(),
                headers,
                null,
                user
        );
    }

    private String readBody(ContentCachingRequestWrapper wrapper) {
        byte[] bytes = wrapper.getContentAsByteArray();
        if (bytes.length == 0) return null;
        String body = new String(bytes, StandardCharsets.UTF_8);
        if (bytes.length >= MAX_BODY_BYTES) {
            body += " ...[truncated]";
        }
        return body;
    }

    /** Attempts to read the authenticated user from Spring Security without a compile-time dependency. */
    private String resolveUser(HttpServletRequest req) {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            Object auth = context.getClass().getMethod("getAuthentication").invoke(context);
            if (auth != null) {
                Object name = auth.getClass().getMethod("getName").invoke(auth);
                return name != null ? name.toString() : null;
            }
        } catch (Exception ignored) {
            // Spring Security not on classpath or no authentication — not an error
        }
        return null;
    }
}
