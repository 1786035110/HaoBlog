package io.haoblog.content.web;

import io.haoblog.content.application.AdminContentService;
import io.haoblog.content.domain.ArticleStatus;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {
    private final AdminContentService service;

    public AdminArticleController(AdminContentService service) { this.service = service; }

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
        CreateRequest body = request == null ? new CreateRequest(null, null, null, null, null, null, null, null, null) : request;
        Response response = Response.from(service.createArticle(body.slug(), body.title(), body.excerpt(), body.markdown(),
                body.seoTitle(), body.seoDescription(), body.categoryId(), body.coverMediaId(), body.tagIds()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) { return Response.from(service.getArticle(id)); }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @RequestBody @Valid UpdateRequest request) {
        return Response.from(service.updateArticle(id, request.version(), request.slug(), request.title(), request.excerpt(),
                request.markdown(), request.seoTitle(), request.seoDescription(), request.categoryId(), request.coverMediaId(), request.tagIds()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        var result = service.deleteArticle(id);
        return result.archived() ? ResponseEntity.ok(Response.from(result.article())) : ResponseEntity.noContent().build();
    }

    private static Sort.Direction parseDirection(String direction) {
        if ("asc".equalsIgnoreCase(direction)) return Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(direction)) return Sort.Direction.DESC;
        throw new IllegalArgumentException("direction must be asc or desc");
    }
}
