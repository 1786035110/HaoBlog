package io.haoblog.comment.web;

import io.haoblog.comment.application.CommentService;
import io.haoblog.comment.domain.CommentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/comments")
public class AdminCommentController {
    private final CommentService service;

    public AdminCommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    public ListResponse list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) CommentStatus status,
                             @RequestParam(required = false) UUID articleId,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "desc") String direction) {
        var result = service.listAdmin(status, articleId, keyword, page, size, parseDirection(direction));
        return new ListResponse(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/{id}")
    public CommentService.AdminCommentDetail get(@PathVariable UUID id) {
        return service.getAdmin(id);
    }

    @PostMapping("/{id}/moderation")
    public CommentService.AdminCommentDetail moderate(@PathVariable UUID id,
                                                      @RequestBody @Valid ModerationRequest request,
                                                      Authentication authentication) {
        try {
            return service.moderate(id, request.version(), request.status(), request.reason(), authentication.getName());
        } catch (OptimisticLockingFailureException exception) {
            throw new io.haoblog.shared.web.ProblemException("COMMENT_VERSION_CONFLICT", "Comment version conflict",
                    "Reload the latest comment before moderating it", service.getAdmin(id).version());
        }
    }

    private static Sort.Direction parseDirection(String direction) {
        if ("asc".equalsIgnoreCase(direction)) return Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(direction)) return Sort.Direction.DESC;
        throw new IllegalArgumentException("direction must be asc or desc");
    }

    public record ListResponse(List<CommentService.AdminComment> items, int page, int size, long total) {}

    public record ModerationRequest(@NotNull Long version, @NotNull CommentStatus status,
                                    @Size(max = 600) String reason) {}
}
