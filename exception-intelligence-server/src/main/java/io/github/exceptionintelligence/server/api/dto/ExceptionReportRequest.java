package io.github.exceptionintelligence.server.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.exceptionintelligence.server.model.ExceptionReport;
import io.github.exceptionintelligence.server.model.RequestContextModel;
import io.github.exceptionintelligence.server.model.StackFrame;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Universal exception report payload sent by any SDK (Java, Node.js, Vue.js, ...).
 * <p>
 * Language-specific details (stack frame file paths, fingerprint) are resolved
 * client-side so the server can be fully language-agnostic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExceptionReportRequest(
        @NotBlank String language,
        String framework,
        String serviceName,
        String environment,
        String timestamp,
        String threadName,
        @NotNull @Valid ExceptionDto exception,
        @Valid RequestContextDto request,
        String fingerprint,
        RepositoryDto repository
) {

    /** Repository information sent by the SDK. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryDto(String owner, String name, String branch) {}


    /** Converts this DTO to the internal domain model used by the pipeline. */
    public ExceptionReport toExceptionReport() {
        List<StackFrame> frames = exception.frames().stream()
                .map(f -> new StackFrame(f.file(), f.function(), f.line(), f.column(), f.isProjectCode()))
                .toList();

        RequestContextModel ctx = null;
        if (request != null) {
            ctx = new RequestContextModel(
                    request.method(),
                    request.uri(),
                    request.queryString(),
                    request.headers(),
                    request.body(),
                    request.authenticatedUser()
            );
        }

        Instant ts;
        try {
            ts = timestamp != null ? Instant.parse(timestamp) : Instant.now();
        } catch (Exception e) {
            ts = Instant.now();
        }

        ExceptionReport.RepositoryInfo repoInfo = null;
        if (repository != null && repository.owner() != null && !repository.owner().isBlank()) {
            repoInfo = new ExceptionReport.RepositoryInfo(
                    repository.owner(), repository.name(), repository.branch());
        }

        return new ExceptionReport(
                language,
                framework,
                serviceName,
                environment,
                ts,
                threadName,
                new ExceptionReport.ExceptionInfo(
                        exception.type(),
                        exception.message(),
                        exception.rawStackTrace(),
                        frames
                ),
                ctx,
                fingerprint,
                repoInfo
        );
    }
}
