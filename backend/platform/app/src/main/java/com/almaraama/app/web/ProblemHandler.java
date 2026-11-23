package com.almaraama.app.web;

import com.almaraama.shared.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every error leaves the API as an RFC 9457 problem with a stable English {@code code}. Messages, stack traces and
 * internal IDs never reach the client (security baseline); unexpected errors are logged on the server only.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProblemHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

    /**
     * A broken business rule: its category gives the HTTP status, its code goes into the problem; {@code Retry-After}
     * is added for lockouts and rate limits.
     */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> business(BusinessException error) {
        HttpStatus status = Problems.status(error.category());
        ProblemDetail problem = Problems.of(status, error.code());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        error.retryAfter().ifPresent(delay -> {
            long seconds = Math.max(1, delay.toSeconds());
            problem.setProperty("retryAfterSeconds", seconds);
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        });
        if ("CSRF_HEADER_MISSING".equals(error.code())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Problems.of(HttpStatus.FORBIDDEN, error.code()));
        }
        return response.body(problem);
    }

    /**
     * A body that fails Bean Validation: {@code 400 VALIDATION_FAILED} with the field and constraint of each violation,
     * never the rejected value.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException error) {
        List<Map<String, String>> violations = error.getBindingResult().getFieldErrors().stream()
                .map(field -> Map.of("field", field.getField(), "code", String.valueOf(field.getCode())))
                .toList();
        return ResponseEntity.badRequest()
                .body(Problems.withViolations(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", violations));
    }

    /** A body that cannot be read (malformed JSON, wrong content type): {@code 400 VALIDATION_FAILED}. */
    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ProblemDetail> unreadable(Exception error) {
        return ResponseEntity.badRequest().body(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MissingRequestHeaderException.class,
            HandlerMethodValidationException.class})
    /**
     * A malformed path or query parameter (for example an invalid UUID), or a missing one: {@code 400
     * VALIDATION_FAILED}.
     */
    ResponseEntity<ProblemDetail> badParameter(Exception error) {
        return ResponseEntity.badRequest().body(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED"));
    }

    /** Two people changed the same object at the same time: the second one reloads (contract VersionConflict). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> concurrentChange(ObjectOptimisticLockingFailureException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(HttpStatus.CONFLICT, "VERSION_CONFLICT"));
    }

    /** A uniqueness rule caught by the database after the use case's own check (two requests at once). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException error) {
        LOG.warn("Integrity violation: {}", error.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(HttpStatus.CONFLICT, "CONFLICT"));
    }

    /** An uploaded file above the size limit: {@code 413 IMPORT_FILE_TOO_LARGE}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException error) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Problems.of(HttpStatus.CONTENT_TOO_LARGE, "IMPORT_FILE_TOO_LARGE"));
    }

    /** An unknown path: {@code 404 NOT_FOUND}, the same answer as something outside the perimeter. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NoResourceFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Problems.of(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** A known path called with the wrong HTTP method: {@code 405 METHOD_NOT_ALLOWED}. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Problems.of(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED"));
    }

    /** Anything else: logged on the server with its stack trace, answered with a bare {@code 500 INTERNAL_ERROR}. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error) {
        LOG.error("Unexpected error", error);
        return ResponseEntity.internalServerError()
                .body(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR"));
    }
}
