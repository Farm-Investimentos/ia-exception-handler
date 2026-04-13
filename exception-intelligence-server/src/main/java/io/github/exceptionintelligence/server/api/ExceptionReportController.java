package io.github.exceptionintelligence.server.api;

import io.github.exceptionintelligence.server.api.dto.ExceptionReportRequest;
import io.github.exceptionintelligence.server.pipeline.ExceptionPipeline;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST API entry point for exception reports.
 * Accepts reports from any SDK (Java, Node.js, Vue.js, …) and hands them off
 * to the async analysis pipeline.
 */
@RestController
@RequestMapping("/v1/exceptions")
public class ExceptionReportController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionReportController.class);

    private final ExceptionPipeline pipeline;

    public ExceptionReportController(ExceptionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Accepts an exception report and schedules asynchronous analysis.
     * Returns 202 Accepted immediately; processing happens in background.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void report(@Valid @RequestBody ExceptionReportRequest request) {
        log.debug("[API] Received exception report — service={} language={} type={}",
                request.serviceName(), request.language(), request.exception().type());
        pipeline.process(request.toExceptionReport());
    }

    /** Health check endpoint. */
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
