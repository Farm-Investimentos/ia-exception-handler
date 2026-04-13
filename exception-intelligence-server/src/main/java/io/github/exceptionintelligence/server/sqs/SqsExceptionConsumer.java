package io.github.exceptionintelligence.server.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.exceptionintelligence.server.api.dto.ExceptionReportRequest;
import io.github.exceptionintelligence.server.pipeline.ExceptionPipeline;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes exception reports from an AWS SQS queue.
 * Alternative ingestion path for services that publish to SQS directly
 * instead of calling the REST API.
 * <p>
 * The message body must be the same JSON schema as {@link ExceptionReportRequest}.
 * Enabled via {@code exception-intelligence.sqs.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "exception-intelligence.sqs", name = "enabled", havingValue = "true")
public class SqsExceptionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SqsExceptionConsumer.class);

    private final ExceptionPipeline pipeline;
    private final ObjectMapper objectMapper;

    public SqsExceptionConsumer(ExceptionPipeline pipeline) {
        this.pipeline = pipeline;
        this.objectMapper = new ObjectMapper();
    }

    @SqsListener("${exception-intelligence.sqs.queue-url}")
    public void consume(String messageBody) {
        try {
            ExceptionReportRequest request = objectMapper.readValue(messageBody, ExceptionReportRequest.class);
            log.debug("[SQS] Received exception report — service={} language={} type={}",
                    request.serviceName(), request.language(), request.exception().type());
            pipeline.process(request.toExceptionReport());
        } catch (Exception ex) {
            log.error("[SQS] Failed to process message: {}", ex.getMessage(), ex);
        }
    }
}
