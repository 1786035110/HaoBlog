package io.haoblog.shared.web;

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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ProblemResponseWriter problemResponseWriter;

    public GlobalExceptionHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemResponse> badRequest(Exception exception) {
        return problemResponseWriter.response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request", "Request parameters are invalid");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ProblemResponse> notFound(NoResourceFoundException exception) {
        return problemResponseWriter.response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemResponse> unexpected(Exception exception) {
        LOG.error("Unhandled request failure traceId={}", MDC.get("traceId"), exception);
        return problemResponseWriter.response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }
}
