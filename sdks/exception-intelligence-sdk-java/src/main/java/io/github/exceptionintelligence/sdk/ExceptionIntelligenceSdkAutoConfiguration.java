package io.github.exceptionintelligence.sdk;

import io.github.exceptionintelligence.sdk.client.ExceptionReportClient;
import io.github.exceptionintelligence.sdk.config.SdkProperties;
import io.github.exceptionintelligence.sdk.handler.GlobalExceptionInterceptor;
import io.github.exceptionintelligence.sdk.handler.RequestContextCaptureFilter;
import io.github.exceptionintelligence.sdk.parser.JavaStackTraceParser;
import io.github.exceptionintelligence.sdk.ratelimit.RateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot auto-configuration for the Exception Intelligence SDK.
 * Add the SDK JAR as a dependency — no additional annotations required.
 *
 * <p>Minimal configuration in {@code application.yml}:
 * <pre>
 * exception-intelligence:
 *   server-url: http://exception-intelligence-server:8090
 *   base-package: com.mycompany.myservice
 * </pre>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "exception-intelligence", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SdkProperties.class)
public class ExceptionIntelligenceSdkAutoConfiguration {

    @Bean
    public RateLimiter sdkRateLimiter(SdkProperties props) {
        return new RateLimiter(props.getRateLimit());
    }

    @Bean
    public JavaStackTraceParser javaStackTraceParser(SdkProperties props) {
        return new JavaStackTraceParser(props.getBasePackage());
    }

    @Bean
    public ExceptionReportClient exceptionReportClient(SdkProperties props) {
        return new ExceptionReportClient(props);
    }

    @Bean
    public GlobalExceptionInterceptor globalExceptionInterceptor(ExceptionReportClient reportClient,
                                                                   RateLimiter rateLimiter,
                                                                   JavaStackTraceParser parser,
                                                                   SdkProperties props) {
        return new GlobalExceptionInterceptor(reportClient, rateLimiter, parser, props);
    }

    @Bean
    public FilterRegistrationBean<RequestContextCaptureFilter> requestContextCaptureFilter() {
        var registration = new FilterRegistrationBean<>(new RequestContextCaptureFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    /**
     * Resolves the service name from the SDK property; falls back to
     * {@code spring.application.name} if not explicitly set.
     */
    @Bean
    public String resolveServiceName(SdkProperties props, Environment env) {
        if (props.getServiceName() != null && !props.getServiceName().isBlank()) {
            return props.getServiceName();
        }
        String appName = env.getProperty("spring.application.name");
        if (appName != null && !appName.isBlank()) {
            props.setServiceName(appName);
        }
        return props.getServiceName();
    }
}
