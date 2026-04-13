package io.github.exceptionintelligence.sdk.handler;

import io.github.exceptionintelligence.sdk.model.RequestContext;

/**
 * ThreadLocal holder for the current HTTP request context.
 */
public class RequestContextHolder {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private RequestContextHolder() {}

    public static void set(RequestContext ctx) { HOLDER.set(ctx); }
    public static RequestContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
