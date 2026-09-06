package io.haoblog.shared.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ProblemResponseWriter problemResponseWriter;

    public GlobalExceptionHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class,
            IllegalArgumentException.class})
    ResponseEntity<ProblemResponse> badRequest(Exception exception) {
        return problemResponseWriter.response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request", "Request parameters are invalid");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemResponse> unreadable(HttpMessageNotReadableException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof RequestBoundaryFilter.RequestBodyTooLargeException) {
                return problemResponseWriter.response(HttpStatus.PAYLOAD_TOO_LARGE,
                        "REQUEST_BODY_TOO_LARGE", "Request body too large", "The request body exceeds the allowed size");
            }
        }
        return badRequest(exception);
    }

    @ExceptionHandler(ProblemException.class)
    ResponseEntity<ProblemResponse> content(ProblemException exception) {
        HttpStatus status = "MEDIA_STORAGE_UNAVAILABLE".equals(exception.getCode()) ? HttpStatus.SERVICE_UNAVAILABLE :
                exception.getCode().endsWith("_NOT_FOUND") ? HttpStatus.NOT_FOUND :
                (Set.of("ARTICLE_PREVIEW_GONE", "MEDIA_UPLOAD_EXPIRED").contains(exception.getCode()) ? HttpStatus.GONE :
                        (Set.of("COMMENTING_DISABLED", "COMMENT_DUPLICATE", "COMMENT_ALREADY_DELETED").contains(exception.getCode())
                                || exception.getCode().contains("CONFLICT") || exception.getCode().endsWith("_IN_USE")
                                ? HttpStatus.CONFLICT :
                                ("COMMENT_DELETE_TOKEN_INVALID".equals(exception.getCode()) ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST)));
        var response = problemResponseWriter.response(status, exception.getCode(), exception.getTitle(), exception.getMessage(),
                exception.getCurrentVersion());
        if (!"MEDIA_STORAGE_UNAVAILABLE".equals(exception.getCode())) return response;
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header("Retry-After", "1").body(response.getBody());
    }


    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemResponse> optimisticLock(OptimisticLockingFailureException exception) {
        return problemResponseWriter.response(HttpStatus.CONFLICT, "ARTICLE_VERSION_CONFLICT", "Article version conflict", "Reload the latest article before saving");
    }

    @ExceptionHandler({org.springframework.dao.QueryTimeoutException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.transaction.CannotCreateTransactionException.class})
    ResponseEntity<ProblemResponse> databaseUnavailable(Exception exception) {
        var response = problemResponseWriter.response(HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_BUSY", "Database temporarily unavailable", "Retry later; check saved state before repeating a write");
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header("Retry-After", "2").header("Cache-Control", "no-store").body(response.getBody());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ProblemResponse> notFound(NoResourceFoundException exception) {
        return problemResponseWriter.response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemResponse> unexpected(Exception exception) {
        LOG.error("Unhandled request failure type={}", exception.getClass().getName());
        return problemResponseWriter.response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }
}
