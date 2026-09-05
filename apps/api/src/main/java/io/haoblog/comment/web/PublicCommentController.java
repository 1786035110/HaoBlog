package io.haoblog.comment.web;

import io.haoblog.comment.application.CommentRateLimitException;
import io.haoblog.comment.application.CommentService;
import io.haoblog.shared.web.ProblemResponse;
import io.haoblog.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicCommentController {
    private static final String VISITOR_COOKIE = "HAOBLOG_VISITOR";
    private static final String DELETE_TOKEN_HEADER = "X-Comment-Delete-Token";

    private final CommentService comments;
    private final ProblemResponseWriter problemResponseWriter;

    public PublicCommentController(CommentService comments, ProblemResponseWriter problemResponseWriter) {
        this.comments = comments;
        this.problemResponseWriter = problemResponseWriter;
    }

    @GetMapping("/articles/{slug}/comments")
    public CommentService.CommentPage list(@PathVariable String slug,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return comments.list(slug, page, size);
    }

    @PostMapping("/articles/{slug}/comments")
    public ResponseEntity<SubmissionResponse> create(@PathVariable String slug,
                                                      @RequestBody CommentRequest request,
                                                      HttpServletRequest httpRequest) {
        CommentService.CreateResult result = comments.create(slug,
                new CommentService.CreateCommand(request.nickname(), request.email(), request.content(), request.parentId(),
                        request.challenge(), request.honeypot(), request.website()),
                visitorCookie(httpRequest), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubmissionResponse(result.id(), result.status().name(), result.createdAt(), result.deleteToken()));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader(value = DELETE_TOKEN_HEADER, required = false) String deleteToken) {
        comments.delete(id, deleteToken);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CommentRateLimitException.class)
    ResponseEntity<ProblemResponse> rateLimited(CommentRateLimitException exception) {
        ProblemResponse body = problemResponseWriter.response(HttpStatus.TOO_MANY_REQUESTS,
                "COMMENT_RATE_LIMITED", "Comment rate limit exceeded",
                "Too many comments have been submitted from this source").getBody();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(ProblemResponseWriter.PROBLEM)
                .body(body);
    }

    private String visitorCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VISITOR_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                        && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public record CommentRequest(String nickname, String email, String content, UUID parentId,
                                 String challenge, String honeypot, String website) {}

    public record SubmissionResponse(UUID id, String status, java.time.Instant createdAt, String deleteToken) {}
}
