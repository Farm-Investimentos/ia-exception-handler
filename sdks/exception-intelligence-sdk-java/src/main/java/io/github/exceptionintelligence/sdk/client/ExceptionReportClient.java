package io.github.exceptionintelligence.sdk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.exceptionintelligence.sdk.config.SdkProperties;
import io.github.exceptionintelligence.sdk.model.ExceptionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP client that forwards exception reports to the Exception Intelligence Server.
 * Sends are fire-and-forget (async) so they never block application threads.
 */
public class ExceptionReportClient {

    private static final Logger log = LoggerFactory.getLogger(ExceptionReportClient.class);

    private final RestClient restClient;
    private final SdkProperties props;
    private final ObjectMapper objectMapper;

    public ExceptionReportClient(SdkProperties props) {
        this.props = props;
        this.objectMapper = new ObjectMapper();

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);

        var builder = RestClient.builder()
                .baseUrl(props.getServerUrl())
                .defaultHeader("Content-Type", "application/json");

        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            builder.defaultHeader("X-API-Key", props.getApiKey());
        }

        this.restClient = builder.requestFactory(factory).build();
    }

    /** Sends the report asynchronously. Failures are logged, never propagated. */
    public void sendAsync(ExceptionReport report) {
        CompletableFuture.runAsync(() -> send(report));
    }

    private void send(ExceptionReport report) {
        try {
            restClient.post()
                    .uri("/v1/exceptions")
                    .body(report)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[SDK] Exception report sent — type={}", report.getException().getType());
        } catch (Exception ex) {
            log.warn("[SDK] Could not reach exception-intelligence-server at {}: {}",
                    props.getServerUrl(), ex.getMessage());
        }
    }
}
