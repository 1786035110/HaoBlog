package io.haoblog.toolbox.application;

import io.haoblog.shared.web.ProblemException;
import io.haoblog.toolbox.domain.Tool;
import io.haoblog.toolbox.domain.ToolCategory;
import io.haoblog.toolbox.domain.ToolComponentKey;
import io.haoblog.toolbox.domain.ToolStatus;
import io.haoblog.toolbox.domain.ToolType;
import io.haoblog.toolbox.persistence.ToolCategoryRepository;
import io.haoblog.toolbox.persistence.ToolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ToolManagementService {
    private final ToolCategoryRepository categories;
    private final ToolRepository tools;
    private final Clock clock;

    public ToolManagementService(ToolCategoryRepository categories, ToolRepository tools, Clock clock) {
        this.categories = categories;
        this.tools = tools;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ToolCategory> listCategories() { return categories.findAllByOrderBySortOrderAscNameAsc(); }

    @Transactional
    public ToolCategory createCategory(String name, String slug, String description, int sortOrder) {
        validateCategory(name, slug, description);
        String normalizedSlug = normalizeSlug(slug);
        if (categories.existsByNameIgnoreCase(name.trim()) || categories.existsBySlug(normalizedSlug)) {
            throw conflict("TOOL_CATEGORY_CONFLICT", "Tool category conflict", "The tool category name or slug is already in use");
        }
        return categories.saveAndFlush(new ToolCategory(name, normalizedSlug, description, sortOrder, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public ToolCategory getCategory(UUID id) { return categories.findById(id).orElseThrow(() -> notFound("TOOL_CATEGORY_NOT_FOUND")); }

    @Transactional
    public ToolCategory updateCategory(UUID id, long version, String name, String slug, String description, int sortOrder) {
        ToolCategory category = getCategory(id);
        requireVersion(category.getVersion(), version, "TOOL_CATEGORY_VERSION_CONFLICT", "Reload the latest tool category before saving");
        validateCategory(name, slug, description);
        String normalizedSlug = normalizeSlug(slug);
        if (categories.existsByNameIgnoreCaseAndIdNot(name.trim(), id) || categories.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw conflict("TOOL_CATEGORY_CONFLICT", "Tool category conflict", "The tool category name or slug is already in use");
        }
        category.update(name, normalizedSlug, description, sortOrder, Instant.now(clock));
        return categories.saveAndFlush(category);
    }

    @Transactional
    public void deleteCategory(UUID id, long version) {
        ToolCategory category = getCategory(id);
        requireVersion(category.getVersion(), version, "TOOL_CATEGORY_VERSION_CONFLICT", "Reload the latest tool category before deleting");
        if (tools.existsByCategoryId(id)) {
            throw conflict("TOOL_CATEGORY_IN_USE", "Tool category in use", "The category is referenced by a tool");
        }
        categories.delete(category);
        categories.flush();
    }

    @Transactional(readOnly = true)
    public Page<Tool> listTools(int page, int size, UUID categoryId, ToolType type, ToolStatus status,
                                String keyword, String sort, Sort.Direction direction) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page/size out of range");
        if (keyword != null && keyword.length() > 240) throw new IllegalArgumentException("keyword is too long");
        String value = keyword == null || keyword.isBlank() ? null : escapeLike(keyword.trim());
        Specification<Tool> specification = (root, query, cb) -> cb.conjunction();
        if (categoryId != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        if (type != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (status != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (value != null) {
            String pattern = "%" + value.toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '!'),
                    cb.like(cb.lower(root.get("slug")), pattern, '!'),
                    cb.like(cb.lower(root.get("description")), pattern, '!')));
        }
        Sort safeSort = Sort.by(direction, allowedSort(sort)).and(Sort.by(direction, "id"));
        return tools.findAll(specification, PageRequest.of(page, size, safeSort));
    }

    @Transactional(readOnly = true)
    public Tool getTool(UUID id) { return tools.findById(id).orElseThrow(() -> notFound("TOOL_NOT_FOUND")); }

    @Transactional
    public Tool createTool(UUID categoryId, ToolType type, ToolStatus status, String title, String slug,
                           String description, String url, String imageUrl, ToolComponentKey componentKey,
                           List<String> tags, int sortOrder) {
        requireCategory(categoryId);
        validateTool(title, slug, description);
        String normalizedSlug = normalizeSlug(slug);
        if (tools.existsBySlug(normalizedSlug)) throw conflict("TOOL_CONFLICT", "Tool conflict", "The tool slug is already in use");
        return tools.saveAndFlush(new Tool(categoryId, type, status, title, normalizedSlug, description, url, imageUrl,
                componentKey, tags, sortOrder, Instant.now(clock)));
    }

    @Transactional
    public Tool updateTool(UUID id, long version, UUID categoryId, ToolType type, ToolStatus status, String title, String slug,
                           String description, String url, String imageUrl, ToolComponentKey componentKey,
                           List<String> tags, int sortOrder) {
        Tool tool = getTool(id);
        requireVersion(tool.getVersion(), version, "TOOL_VERSION_CONFLICT", "Reload the latest tool before saving");
        requireCategory(categoryId);
        validateTool(title, slug, description);
        String normalizedSlug = normalizeSlug(slug);
        if (tools.existsBySlugAndIdNot(normalizedSlug, id)) throw conflict("TOOL_CONFLICT", "Tool conflict", "The tool slug is already in use");
        tool.update(categoryId, type, status, title, normalizedSlug, description, url, imageUrl, componentKey,
                tags, sortOrder, Instant.now(clock));
        return tools.saveAndFlush(tool);
    }

    @Transactional
    public void deleteTool(UUID id, long version) {
        Tool tool = getTool(id);
        requireVersion(tool.getVersion(), version, "TOOL_VERSION_CONFLICT", "Reload the latest tool before deleting");
        tools.delete(tool);
        tools.flush();
    }

    public long currentCategoryVersion(UUID id) { return getCategory(id).getVersion(); }
    public long currentToolVersion(UUID id) { return getTool(id).getVersion(); }

    private void requireCategory(UUID id) {
        if (id == null || !categories.existsById(id)) throw notFound("TOOL_CATEGORY_NOT_FOUND");
    }

    private static void validateCategory(String name, String slug, String description) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw new IllegalArgumentException("name is invalid");
        if (description != null && description.length() > 600) throw new IllegalArgumentException("description is too long");
        normalizeSlug(slug);
    }

    private static void validateTool(String title, String slug, String description) {
        if (title == null || title.isBlank() || title.trim().length() > 160) throw new IllegalArgumentException("title is invalid");
        if (description != null && description.length() > 600) throw new IllegalArgumentException("description is too long");
        normalizeSlug(slug);
    }

    private static void requireVersion(long current, long requested, String code, String detail) {
        if (current != requested) throw new ProblemException(code, "Version conflict", detail, current);
    }

    private static String escapeLike(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    private static String normalizeSlug(String value) { return io.haoblog.toolbox.domain.ToolSlug.normalizeRequired(value); }
    private static String allowedSort(String sort) {
        return switch (sort == null || sort.isBlank() ? "sortOrder" : sort) {
            case "sortOrder", "createdAt", "updatedAt", "title" -> sort == null || sort.isBlank() ? "sortOrder" : sort;
            default -> throw new IllegalArgumentException("sort must be sortOrder, createdAt, updatedAt or title");
        };
    }
    private static ProblemException notFound(String code) { return new ProblemException(code, "Resource not found", "The requested toolbox resource was not found"); }
    private static ProblemException conflict(String code, String title, String detail) { return new ProblemException(code, title, detail); }
}
