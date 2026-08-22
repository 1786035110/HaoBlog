package io.haoblog.content.web;

import io.haoblog.content.application.AdminContentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static io.haoblog.content.web.AdminTaxonomyDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTaxonomyController {
    private final AdminContentService service;

    public AdminTaxonomyController(AdminContentService service) { this.service = service; }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() { return service.listCategories().stream().map(CategoryResponse::from).toList(); }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@RequestBody @Valid CategoryRequest request) {
        CategoryResponse response = CategoryResponse.from(service.createCategory(request.name(), request.slug(), request.description(), request.sortOrder()));
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/categories/" + response.id())).body(response);
    }

    @GetMapping("/categories/{id}")
    public CategoryResponse category(@PathVariable UUID id) { return CategoryResponse.from(service.getCategory(id)); }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable UUID id, @RequestBody @Valid CategoryRequest request) {
        return CategoryResponse.from(service.updateCategory(id, request.name(), request.slug(), request.description(), request.sortOrder()));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) { service.deleteCategory(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/tags")
    public List<TagResponse> tags() { return service.listTags().stream().map(TagResponse::from).toList(); }

    @PostMapping("/tags")
    public ResponseEntity<TagResponse> createTag(@RequestBody @Valid TagRequest request) {
        TagResponse response = TagResponse.from(service.createTag(request.name(), request.slug()));
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/tags/" + response.id())).body(response);
    }

    @GetMapping("/tags/{id}")
    public TagResponse tag(@PathVariable UUID id) { return TagResponse.from(service.getTag(id)); }

    @PutMapping("/tags/{id}")
    public TagResponse updateTag(@PathVariable UUID id, @RequestBody @Valid TagRequest request) {
        return TagResponse.from(service.updateTag(id, request.name(), request.slug()));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable UUID id) { service.deleteTag(id); return ResponseEntity.noContent().build(); }
}
