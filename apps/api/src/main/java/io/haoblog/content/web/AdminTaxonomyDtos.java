package io.haoblog.content.web;

import io.haoblog.content.domain.Category;
import io.haoblog.content.domain.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AdminTaxonomyDtos {
    private AdminTaxonomyDtos() {}

    public record CategoryRequest(@NotBlank @Size(max = 120) String name,
                                  @NotBlank @Size(max = 160) String slug,
                                  @Size(max = 600) String description,
                                  int sortOrder) {}

    public record TagRequest(@NotBlank @Size(max = 120) String name,
                             @NotBlank @Size(max = 160) String slug) {}

    public record CategoryResponse(UUID id, String name, String slug, String description,
                                   int sortOrder, Instant createdAt, Instant updatedAt) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                    category.getDescription(), category.getSortOrder(), category.getCreatedAt(), category.getUpdatedAt());
        }
    }

    public record TagResponse(UUID id, String name, String slug, Instant createdAt, Instant updatedAt) {
        static TagResponse from(Tag tag) {
            return new TagResponse(tag.getId(), tag.getName(), tag.getSlug(), tag.getCreatedAt(), tag.getUpdatedAt());
        }
    }
}
