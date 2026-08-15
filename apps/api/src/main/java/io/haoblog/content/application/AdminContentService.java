package io.haoblog.content.application;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.domain.Category;
import io.haoblog.content.domain.Slug;
import io.haoblog.content.domain.Tag;
import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.content.persistence.CategoryRepository;
import io.haoblog.content.persistence.TagRepository;
import io.haoblog.shared.web.ProblemException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminContentService {
    private static final int MAX_MARKDOWN_BYTES = 1024 * 1024;
    private final ArticleRepository articles;
    private final ArticleRevisionRepository revisions;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final Clock clock;

    public AdminContentService(ArticleRepository articles, ArticleRevisionRepository revisions,
                               CategoryRepository categories, TagRepository tags, Clock clock) {
        this.articles = articles;
        this.revisions = revisions;
        this.categories = categories;
        this.tags = tags;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Article> listArticles(int page, int size, ArticleStatus status, String keyword, Sort.Direction direction) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page/size out of range");
        if (keyword != null && keyword.length() > 240) throw new IllegalArgumentException("keyword is too long");
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var pageable = PageRequest.of(page, size, Sort.by(direction, "updatedAt").and(Sort.by(direction, "id")));
        return articles.findAdminArticles(status, normalizedKeyword, pageable);
    }

    @Transactional
    public Article createArticle(String slug, String title, String excerpt, String markdown,
                                 String seoTitle, String seoDescription, UUID categoryId,
                                 UUID coverMediaId, List<UUID> tagIds) {
        String resolvedTitle = title == null || title.isBlank() ? "未命名草稿" : title.trim();
        String resolvedMarkdown = markdown == null ? "" : markdown;
        validateArticle(resolvedTitle, resolvedMarkdown, excerpt, seoTitle, seoDescription);
        String normalizedSlug = Slug.normalizeNullable(slug);
        if (normalizedSlug != null && articles.existsBySlug(normalizedSlug)) {
            throw conflict("ARTICLE_SLUG_CONFLICT", "Article slug conflict", "The article slug is already in use");
        }
        Set<Tag> resolvedTags = resolveTags(tagIds);
        requireCategory(categoryId);
        Instant now = Instant.now(clock);
        Article article = new Article(normalizedSlug, resolvedTitle, excerpt, resolvedMarkdown,
                ArticleStatus.DRAFT, null, now);
        article.updateWorkingCopy(normalizedSlug, resolvedTitle, excerpt, resolvedMarkdown, seoTitle, seoDescription,
                null, categoryId, coverMediaId, now);
        article.replaceTags(resolvedTags);
        return articles.saveAndFlush(article);
    }

    @Transactional(readOnly = true)
    public Article getArticle(UUID id) {
        return articles.findWithTagsById(id).orElseThrow(() -> notFound("ARTICLE_NOT_FOUND", "Article not found"));
    }

    @Transactional
    public Article updateArticle(UUID id, long version, String slug, String title, String excerpt, String markdown,
                                 String seoTitle, String seoDescription, UUID categoryId,
                                 UUID coverMediaId, List<UUID> tagIds) {
        Article article = getArticle(id);
        if (article.getVersion() != version) {
            throw conflict("ARTICLE_VERSION_CONFLICT", "Article version conflict", "Reload the latest article before saving");
        }
        String resolvedSlug = Slug.normalizeNullable(slug);
        if (resolvedSlug != null && articles.existsBySlugAndIdNot(resolvedSlug, id)) {
            throw conflict("ARTICLE_SLUG_CONFLICT", "Article slug conflict", "The article slug is already in use");
        }
        validateArticle(title, markdown, excerpt, seoTitle, seoDescription);
        Set<Tag> resolvedTags = resolveTags(tagIds);
        requireCategory(categoryId);
        article.updateWorkingCopy(resolvedSlug, title.trim(), excerpt, markdown, seoTitle, seoDescription,
                article.getScheduledAt(), categoryId, coverMediaId, Instant.now(clock));
        article.replaceTags(resolvedTags);
        return articles.saveAndFlush(article);
    }

    @Transactional
    public DeleteResult deleteArticle(UUID id) {
        Article article = getArticle(id);
        boolean physical = article.getStatus() == ArticleStatus.DRAFT
                && article.getPublishedRevisionId() == null
                && !revisions.existsByArticleId(id);
        if (physical) {
            articles.delete(article);
            articles.flush();
            return new DeleteResult(false, null);
        }
        article.archive(Instant.now(clock));
        return new DeleteResult(true, articles.saveAndFlush(article));
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() { return categories.findAllByOrderBySortOrderAscNameAsc(); }

    @Transactional
    public Category createCategory(String name, String slug, String description, int sortOrder) {
        validateTaxonomy(name, slug);
        String normalizedSlug = Slug.normalizeRequired(slug);
        if (categories.existsByNameIgnoreCase(name) || categories.existsBySlug(normalizedSlug)) {
            throw conflict("CATEGORY_CONFLICT", "Category conflict", "The category name or slug is already in use");
        }
        return categories.saveAndFlush(new Category(name.trim(), normalizedSlug, description, sortOrder, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Category getCategory(UUID id) { return categories.findById(id).orElseThrow(() -> notFound("CATEGORY_NOT_FOUND", "Category not found")); }

    @Transactional
    public Category updateCategory(UUID id, String name, String slug, String description, int sortOrder) {
        Category category = getCategory(id);
        validateTaxonomy(name, slug);
        String normalizedSlug = Slug.normalizeRequired(slug);
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id) || categories.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw conflict("CATEGORY_CONFLICT", "Category conflict", "The category name or slug is already in use");
        }
        category.update(name.trim(), normalizedSlug, description, sortOrder, Instant.now(clock));
        return categories.saveAndFlush(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = getCategory(id);
        if (articles.existsByCategoryId(id)) throw conflict("CATEGORY_IN_USE", "Category in use", "The category is referenced by an article");
        categories.delete(category);
        categories.flush();
    }

    @Transactional(readOnly = true)
    public List<Tag> listTags() { return tags.findAllByOrderByNameAsc(); }

    @Transactional
    public Tag createTag(String name, String slug) {
        validateTaxonomy(name, slug);
        String normalizedSlug = Slug.normalizeRequired(slug);
        if (tags.existsByNameIgnoreCase(name) || tags.existsBySlug(normalizedSlug)) {
            throw conflict("TAG_CONFLICT", "Tag conflict", "The tag name or slug is already in use");
        }
        return tags.saveAndFlush(new Tag(name.trim(), normalizedSlug, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Tag getTag(UUID id) { return tags.findById(id).orElseThrow(() -> notFound("TAG_NOT_FOUND", "Tag not found")); }

    @Transactional
    public Tag updateTag(UUID id, String name, String slug) {
        Tag tag = getTag(id);
        validateTaxonomy(name, slug);
        String normalizedSlug = Slug.normalizeRequired(slug);
        if (tags.existsByNameIgnoreCaseAndIdNot(name, id) || tags.existsBySlugAndIdNot(normalizedSlug, id)) {
            throw conflict("TAG_CONFLICT", "Tag conflict", "The tag name or slug is already in use");
        }
        tag.update(name.trim(), normalizedSlug, Instant.now(clock));
        return tags.saveAndFlush(tag);
    }

    @Transactional
    public void deleteTag(UUID id) {
        Tag tag = getTag(id);
        if (articles.existsByTags_Id(id)) throw conflict("TAG_IN_USE", "Tag in use", "The tag is referenced by an article");
        tags.delete(tag);
        tags.flush();
    }

    private Set<Tag> resolveTags(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return new LinkedHashSet<>();
        Set<UUID> distinct = new LinkedHashSet<>(tagIds);
        if (distinct.size() != tagIds.size()) throw new IllegalArgumentException("Duplicate tag ids are not allowed");
        List<Tag> found = tags.findAllById(distinct);
        if (found.size() != distinct.size()) throw new ProblemException("TAG_NOT_FOUND", "Tag not found", "One or more tags do not exist");
        return new LinkedHashSet<>(found);
    }

    private void requireCategory(UUID categoryId) {
        if (categoryId != null && !categories.existsById(categoryId)) {
            throw notFound("CATEGORY_NOT_FOUND", "Category not found");
        }
    }

    private static void validateArticle(String title, String markdown, String excerpt, String seoTitle, String seoDescription) {
        if (title == null || title.isBlank() || title.length() > 240) throw new IllegalArgumentException("title is invalid");
        if (markdown == null || markdown.getBytes(StandardCharsets.UTF_8).length > MAX_MARKDOWN_BYTES) throw new IllegalArgumentException("markdown is too large");
        if (excerpt != null && excerpt.length() > 600) throw new IllegalArgumentException("excerpt is too long");
        if (seoTitle != null && seoTitle.length() > 240) throw new IllegalArgumentException("seoTitle is too long");
        if (seoDescription != null && seoDescription.length() > 600) throw new IllegalArgumentException("seoDescription is too long");
    }

    private static void validateTaxonomy(String name, String slug) {
        if (name == null || name.isBlank() || name.trim().length() > 120) throw new IllegalArgumentException("name is invalid");
        if (slug == null || slug.isBlank() || slug.length() > 160) throw new IllegalArgumentException("slug is invalid");
    }

    private static ProblemException notFound(String code, String detail) { return new ProblemException(code, "Resource not found", detail); }
    private static ProblemException conflict(String code, String title, String detail) { return new ProblemException(code, title, detail); }

    public record DeleteResult(boolean archived, Article article) {}
}
