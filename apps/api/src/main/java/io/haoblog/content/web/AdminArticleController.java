package io.haoblog.content.web;

import io.haoblog.content.application.AdminContentService;
import io.haoblog.content.application.ArticleWorkflowService;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.shared.web.ProblemException;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

import static io.haoblog.content.web.AdminArticleDtos.*;
import static io.haoblog.content.web.AdminArticleVersionDtos.*;
import static io.haoblog.content.web.ArticleWorkflowDtos.*;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {
    private final AdminContentService service;
    private final ArticleWorkflowService workflow;

    public AdminArticleController(AdminContentService service, ArticleWorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
    }

    @GetMapping
    public ListResponse list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) ArticleStatus status,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "desc") String direction) {
        Sort.Direction sortDirection = parseDirection(direction);
        var result = service.listArticles(page, size, status, keyword, sortDirection);
        return new ListResponse(result.getContent().stream().map(Summary::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping
    public ResponseEntity<Response> create(@RequestBody(required = false) @Valid CreateRequest request) {
        CreateRequest body = request == null ? new CreateRequest(null, null, null, null, null, null, null, null, null, null) : request;
        Response response = Response.from(service.createArticle(body.slug(), body.title(), body.excerpt(), body.markdown(),
                body.seoTitle(), body.seoDescription(), body.scheduledAt(), body.categoryId(), body.coverMediaId(), body.tagIds()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) { return Response.from(service.getArticle(id)); }

    @GetMapping("/{id}/versions")
    public VersionListResponse listVersions(@PathVariable UUID id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        var result = service.listRevisions(id, page, size);
        return new VersionListResponse(result.getContent().stream().map(VersionSummary::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/{id}/versions/{revisionId}")
    public VersionResponse getVersion(@PathVariable UUID id, @PathVariable UUID revisionId) {
        return VersionResponse.from(service.getRevision(id, revisionId));
    }

    @PostMapping("/{id}/versions/{revisionId}/restore")
    public Response restoreVersion(@PathVariable UUID id, @PathVariable UUID revisionId,
                                   @RequestBody @Valid VersionRequest request) {
        try {
            return Response.from(service.restoreRevision(id, revisionId, request.version()));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        }
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @RequestBody @Valid UpdateRequest request) {
        try {
            return Response.from(service.updateArticle(id, request.version(), request.slug(), request.title(), request.excerpt(),
                    request.markdown(), request.seoTitle(), request.seoDescription(), request.scheduledAt(), request.categoryId(), request.coverMediaId(), request.tagIds()));
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("ARTICLE_VERSION_CONFLICT", "Article version conflict",
                    "Reload the latest article before saving", service.currentVersion(id));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        var result = service.deleteArticle(id);
        return result.archived() ? ResponseEntity.ok(Response.from(result.article())) : ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    public ActionResponse publish(@PathVariable UUID id, @RequestBody @Valid VersionRequest request) {
        try {
            return ActionResponse.from(workflow.publish(id, request.version()));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        }
    }

    @PostMapping("/{id}/schedule")
    public ActionResponse schedule(@PathVariable UUID id, @RequestBody @Valid ScheduleRequest request) {
        try {
            return ActionResponse.from(workflow.schedule(id, request.version(), request.scheduledAt()));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        }
    }

    @PostMapping("/{id}/archive")
    public ActionResponse archive(@PathVariable UUID id, @RequestBody @Valid VersionRequest request) {
        try {
            return ActionResponse.from(workflow.archive(id, request.version()));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        }
    }

    @PostMapping("/{id}/draft")
    public ActionResponse draft(@PathVariable UUID id, @RequestBody @Valid VersionRequest request) {
        try {
            return ActionResponse.from(workflow.returnToDraft(id, request.version()));
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict(id);
        }
    }

    @PostMapping("/{id}/preview-tokens")
    public ResponseEntity<PreviewTokenResponse> createPreviewToken(@PathVariable UUID id,
                                                                    @RequestBody @Valid VersionRequest request) {
        PreviewTokenResponse response = PreviewTokenResponse.from(workflow.createPreviewToken(id, request.version()));
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{id}/preview-tokens/{tokenId}")
    public ResponseEntity<Void> revokePreviewToken(@PathVariable UUID id, @PathVariable UUID tokenId) {
        workflow.revokePreviewToken(id, tokenId);
        return ResponseEntity.noContent().build();
    }

    private ProblemException versionConflict(UUID id) {
        return new ProblemException("ARTICLE_VERSION_CONFLICT", "Article version conflict",
                "Reload the latest article before changing its publication state", service.currentVersion(id));
    }

    private static Sort.Direction parseDirection(String direction) {
        if ("asc".equalsIgnoreCase(direction)) return Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(direction)) return Sort.Direction.DESC;
        throw new IllegalArgumentException("direction must be asc or desc");
    }
}
