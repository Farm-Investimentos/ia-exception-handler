package io.github.exceptionintelligence.sdk.handler;

import io.github.exceptionintelligence.sdk.client.ExceptionReportClient;
import io.github.exceptionintelligence.sdk.config.SdkProperties;
import io.github.exceptionintelligence.sdk.model.ExceptionReport;
import io.github.exceptionintelligence.sdk.model.RequestContext;
import io.github.exceptionintelligence.sdk.model.StackFrame;
import io.github.exceptionintelligence.sdk.parser.JavaStackTraceParser;
import io.github.exceptionintelligence.sdk.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;
import java.util.List;

/**
 * Spring MVC exception resolver that observes (but does not handle) exceptions.
 * Forwards exception data to the Exception Intelligence Server for analysis.
 * Also installed as the default uncaught exception handler to capture exceptions
 * from non-HTTP threads (async tasks, schedulers).
 */
public class GlobalExceptionInterceptor
        implements HandlerExceptionResolver, Ordered, Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionInterceptor.class);

    private final ExceptionReportClient reportClient;
    private final RateLimiter rateLimiter;
    private final JavaStackTraceParser stackParser;
    private final SdkProperties props;

    public GlobalExceptionInterceptor(ExceptionReportClient reportClient,
                                       RateLimiter rateLimiter,
                                       JavaStackTraceParser stackParser,
                                       SdkProperties props) {
        this.reportClient = reportClient;
        this.rateLimiter = rateLimiter;
        this.stackParser = stackParser;
        this.props = props;

        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    // ── HandlerExceptionResolver ──────────────────────────────────────────

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                          Object handler, Exception ex) {
        RequestContext ctx = RequestContextHolder.get();
        sendIfAllowed(ex, ctx);
        return null; // let other resolvers / @ControllerAdvice handle the response
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    // ── UncaughtExceptionHandler ──────────────────────────────────────────

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        sendIfAllowed(ex, null);
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private void sendIfAllowed(Throwable ex, RequestContext ctx) {
        if (!rateLimiter.tryConsume()) {
            log.debug("[SDK] Rate limit exceeded — dropping exception report");
            return;
        }

        try {
            List<StackFrame> frames = stackParser.parse(ex);
            String fingerprint = stackParser.fingerprint(ex, frames);
            String rawStack = stackParser.rawStackTrace(ex);

            var repo = props.getRepository();
            ExceptionReport.RepositoryInfo repoInfo = null;
            if (repo != null && repo.getOwner() != null && !repo.getOwner().isBlank()) {
                repoInfo = new ExceptionReport.RepositoryInfo(
                        repo.getOwner(), repo.getName(), repo.getBranch());
            }

            ExceptionReport report = ExceptionReport.builder()
                    .language("java")
                    .framework(props.getFramework())
                    .serviceName(resolveServiceName())
                    .environment(props.getEnvironment())
                    .timestamp(Instant.now())
                    .threadName(Thread.currentThread().getName())
                    .exception(new ExceptionReport.ExceptionInfo(
                            ex.getClass().getName(),
                            ex.getMessage(),
                            rawStack,
                            frames
                    ))
                    .request(ctx)
                    .fingerprint(fingerprint)
                    .repository(repoInfo)
                    .build();

            reportClient.sendAsync(report);
        } catch (Exception sendEx) {
            log.warn("[SDK] Failed to send exception report: {}", sendEx.getMessage());
        }
    }

    private String resolveServiceName() {
        String name = props.getServiceName();
        return (name != null && !name.isBlank()) ? name : "unknown-service";
    }
}
