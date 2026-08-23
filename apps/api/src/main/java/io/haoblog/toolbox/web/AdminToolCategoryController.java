package io.haoblog.toolbox.web;

import io.haoblog.shared.web.ProblemException;
import io.haoblog.toolbox.application.ToolManagementService;
import io.haoblog.toolbox.domain.ToolCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.OptimisticLockingFailureException;
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

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tool-categories")
public class AdminToolCategoryController {
    private final ToolManagementService service;

    public AdminToolCategoryController(ToolManagementService service) { this.service = service; }

    @GetMapping
    public List<Response> list() { return service.listCategories().stream().map(Response::from).toList(); }

    @PostMapping
    public ResponseEntity<Response> create(@RequestBody @Valid CreateRequest request) {
        Response result = Response.from(service.createCategory(request.name(), request.slug(), request.description(), sortOrderOrDefault(request.sortOrder())));
        return ResponseEntity.created(URI.create("/api/v1/admin/tool-categories/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable UUID id) { return Response.from(service.getCategory(id)); }

    @PutMapping("/{id}")
    public Response update(@PathVariable UUID id, @RequestBody @Valid UpdateRequest request) {
        try {
            return Response.from(service.updateCategory(id, request.version(), request.name(), request.slug(), request.description(), sortOrderOrDefault(request.sortOrder())));
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("TOOL_CATEGORY_VERSION_CONFLICT", "Version conflict", "Reload the latest tool category before saving", service.currentCategoryVersion(id));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam long version) {
        try {
            service.deleteCategory(id, version);
            return ResponseEntity.noContent().build();
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("TOOL_CATEGORY_VERSION_CONFLICT", "Version conflict", "Reload the latest tool category before deleting", service.currentCategoryVersion(id));
        }
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 160) String slug,
                                @Size(max = 600) String description, Integer sortOrder) {}
    public record UpdateRequest(@NotNull Long version, @NotBlank @Size(max = 120) String name,
                                @NotBlank @Size(max = 160) String slug, @Size(max = 600) String description,
                                Integer sortOrder) {}
    public record Response(UUID id, String name, String slug, String description, int sortOrder, long version,
                           Instant createdAt, Instant updatedAt) {
        static Response from(ToolCategory value) { return new Response(value.getId(), value.getName(), value.getSlug(), value.getDescription(),
                value.getSortOrder(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt()); }
    }

    private static int sortOrderOrDefault(Integer sortOrder) { return sortOrder == null ? 0 : sortOrder; }
}
