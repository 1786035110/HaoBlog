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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Transactional(readOnly = true)
    public PublicTools listPublicTools(String category, ToolType type, String keyword) {
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim();
        if (normalizedCategory != null && normalizedCategory.length() > 160) {
            throw new IllegalArgumentException("category is too long");
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 240) {
            throw new IllegalArgumentException("keyword is too long");
        }
        return queryPublicTools(normalizedCategory, type, normalizedKeyword);
    }

    @Transactional(readOnly = true)
    public GardenToolBatch listPublicGardenTools(int limit, int tagLimit) {
        if (limit < 0 || limit > 200 || tagLimit < 1 || tagLimit > 12) throw new IllegalArgumentException("garden limit out of range");
        int querySize = Math.max(1, limit + 1);
        List<Tool> activeTools = tools.findAllByStatus(ToolStatus.ACTIVE,
                PageRequest.of(0, querySize, Sort.by(Sort.Direction.ASC, "sortOrder", "title", "id")));
        Map<UUID, ToolCategory> categoryById = categories.findAllById(
                activeTools.stream().map(Tool::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(ToolCategory::getId, categoryValue -> categoryValue));
        boolean tagsTruncated = activeTools.stream().limit(limit)
                .anyMatch(tool -> tool.getTags() != null && tool.getTags().size() > tagLimit);
        List<GardenTool> result = activeTools.stream().limit(limit).map(tool -> {
            ToolCategory category = categoryById.get(tool.getCategoryId());
            if (category == null) return null;
            return new GardenTool(tool.getId(), tool.getTitle(), tool.getSlug(), tool.getDescription(),
                    category.getName(), category.getSlug(), tool.getTags() == null ? List.of() : tool.getTags().stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER).limit(tagLimit).toList(), tool.getSortOrder());
        }).filter(java.util.Objects::nonNull).toList();
        return new GardenToolBatch(result, activeTools.size() > limit || tagsTruncated);
    }

    private PublicTools queryPublicTools(String normalizedCategory, ToolType type, String normalizedKeyword) {

        List<Tool> activeTools = tools.findAllByStatus(ToolStatus.ACTIVE,
                Sort.by(Sort.Direction.ASC, "sortOrder", "title", "id"));
        Map<UUID, ToolCategory> categoryById = categories.findAllById(
                activeTools.stream().map(Tool::getCategoryId).distinct().toList()).stream()
                .collect(Collectors.toMap(ToolCategory::getId, categoryValue -> categoryValue));
        Predicate<Tool> categoryFilter = categoryPredicate(normalizedCategory, categoryById);
        String keywordValue = normalizedKeyword == null ? null : normalizedKeyword.toLowerCase(Locale.ROOT);
        List<PublicTool> items = activeTools.stream()
                .filter(tool -> type == null || tool.getType() == type)
                .filter(categoryFilter)
                .filter(tool -> keywordValue == null || containsKeyword(tool, keywordValue))
                .map(tool -> PublicTool.from(tool, categoryById.get(tool.getCategoryId())))
                .filter(tool -> tool.category() != null)
                .toList();
        List<ToolCategory> validCategories = items.stream().map(PublicTool::category).distinct()
                .sorted(java.util.Comparator.comparingInt(ToolCategory::getSortOrder)
                        .thenComparing(ToolCategory::getName)
                        .thenComparing(ToolCategory::getId))
                .toList();
        return new PublicTools(items, validCategories);
    }

    private static Predicate<Tool> categoryPredicate(String category, Map<UUID, ToolCategory> categoryById) {
        if (category == null) return ignored -> true;
        return tool -> {
            ToolCategory value = categoryById.get(tool.getCategoryId());
            return value != null && (value.getSlug().equalsIgnoreCase(category)
                    || value.getId().toString().equalsIgnoreCase(category));
        };
    }

    private static boolean containsKeyword(Tool tool, String keyword) {
        return Stream.of(tool.getTitle(), tool.getSlug(), tool.getDescription())
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(keyword))
                || tool.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(keyword));
    }

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

    public record PublicTools(List<PublicTool> items, List<ToolCategory> categories) {}

    public record PublicTool(UUID id, ToolCategory category, ToolType type, String title, String slug,
                             String description, String url, String imageUrl, String componentKey, List<String> tags,
                             int sortOrder) {
        static PublicTool from(Tool tool, ToolCategory category) {
            return new PublicTool(tool.getId(), category, tool.getType(), tool.getTitle(), tool.getSlug(), tool.getDescription(),
                    tool.getUrl(), tool.getImageUrl(), tool.getComponentKey() == null ? null : tool.getComponentKey().getValue(),
                    tool.getTags(), tool.getSortOrder());
        }
    }

    public record GardenTool(UUID id, String title, String slug, String description,
                             String categoryName, String categorySlug, List<String> tags, int sortOrder) {}
    public record GardenToolBatch(List<GardenTool> items, boolean truncated) {}
}
