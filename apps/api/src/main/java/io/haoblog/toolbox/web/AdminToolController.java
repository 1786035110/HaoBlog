package io.haoblog.toolbox.web;

import io.haoblog.shared.web.ProblemException;
import io.haoblog.toolbox.application.ToolManagementService;
import io.haoblog.toolbox.domain.Tool;
import io.haoblog.toolbox.domain.ToolCategory;
import io.haoblog.toolbox.domain.ToolComponentKey;
import io.haoblog.toolbox.domain.ToolStatus;
import io.haoblog.toolbox.domain.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tools")
public class AdminToolController {
    private final ToolManagementService service;

    public AdminToolController(ToolManagementService service) { this.service = service; }

    @GetMapping
    public ListResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) ToolType type,
                             @RequestParam(required = false) ToolStatus status, @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "sortOrder") String sort, @RequestParam(defaultValue = "asc") String direction) {
        PageResult result = new PageResult(service.listTools(page, size, categoryId, type, status, keyword, sort, parseDirection(direction)));
        return new ListResponse(result.items(), result.page(), result.size(), result.total());
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) { return Response.from(service.getTool(id)); }

    @PostMapping
    public org.springframework.http.ResponseEntity<Response> create(@RequestBody @Valid Request request) {
        Response result = Response.from(service.createTool(request.categoryId(), request.type(), request.status(), request.title(), request.slug(),
                request.description(), request.url(), request.imageUrl(), ToolComponentKey.fromValue(request.componentKey()), request.tags(), sortOrderOrDefault(request.sortOrder())));
        return org.springframework.http.ResponseEntity.created(java.net.URI.create("/api/v1/admin/tools/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @RequestBody @Valid UpdateRequest request) {
        try {
            return Response.from(service.updateTool(id, request.version(), request.categoryId(), request.type(), request.status(), request.title(), request.slug(),
                    request.description(), request.url(), request.imageUrl(), ToolComponentKey.fromValue(request.componentKey()), request.tags(), sortOrderOrDefault(request.sortOrder())));
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("TOOL_VERSION_CONFLICT", "Version conflict", "Reload the latest tool before saving", service.currentToolVersion(id));
        }
    }

    @DeleteMapping("/{id}")
    public org.springframework.http.ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version) {
        try {
            service.deleteTool(id, version);
            return org.springframework.http.ResponseEntity.noContent().build();
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("TOOL_VERSION_CONFLICT", "Version conflict", "Reload the latest tool before deleting", service.currentToolVersion(id));
        }
    }

    private static Sort.Direction parseDirection(String direction) {
        if ("asc".equalsIgnoreCase(direction)) return Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(direction)) return Sort.Direction.DESC;
        throw new IllegalArgumentException("direction must be asc or desc");
    }

    public record Request(@NotNull UUID categoryId, @NotNull ToolType type, @NotNull ToolStatus status,
                          @NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 160) String slug,
                          @Size(max = 600) String description, @Size(max = 2048) String url,
                          @Size(max = 2048) String imageUrl, @Size(max = 32) String componentKey,
                          @Size(max = 32) List<@NotBlank @Size(max = 64) String> tags, Integer sortOrder) {}
    public record UpdateRequest(@NotNull Long version, @NotNull UUID categoryId, @NotNull ToolType type, @NotNull ToolStatus status,
                                @NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 160) String slug,
                                @Size(max = 600) String description, @Size(max = 2048) String url,
                                @Size(max = 2048) String imageUrl, @Size(max = 32) String componentKey,
                                @Size(max = 32) List<@NotBlank @Size(max = 64) String> tags, Integer sortOrder) {}
    public record ListResponse(List<Response> items, int page, int size, long total) {}
    private record PageResult(List<Response> items, int page, int size, long total) {
        PageResult(org.springframework.data.domain.Page<Tool> page) { this(page.getContent().stream().map(Response::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements()); }
    }
    public record Response(UUID id, UUID categoryId, ToolType type, ToolStatus status, String title, String slug,
                           String description, String url, String imageUrl, String componentKey, List<String> tags,
                           int sortOrder, long version, Instant createdAt, Instant updatedAt) {
        static Response from(Tool value) { return new Response(value.getId(), value.getCategoryId(), value.getType(), value.getStatus(), value.getTitle(), value.getSlug(),
                value.getDescription(), value.getUrl(), value.getImageUrl(), value.getComponentKey() == null ? null : value.getComponentKey().getValue(), value.getTags(), value.getSortOrder(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt()); }
        }

    private static int sortOrderOrDefault(Integer sortOrder) { return sortOrder == null ? 0 : sortOrder; }
}
