package io.haoblog.site.application;

import io.haoblog.content.application.ArticleService;
import io.haoblog.toolbox.application.ToolManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GardenGraphService {
    static final int MAX_NODES = 200;
    static final int MAX_EDGES = 600;
    static final int MAX_TAGS_PER_CONTENT = 12;

    private final ArticleService articles;
    private final ToolManagementService tools;

    public GardenGraphService(ArticleService articles, ToolManagementService tools) {
        this.articles = articles;
        this.tools = tools;
    }

    @Transactional(readOnly = true)
    public Graph get() {
        var articleBatch = articles.listPublicGardenArticles(MAX_NODES, MAX_TAGS_PER_CONTENT);
        int remaining = MAX_NODES - articleBatch.items().size();
        var toolBatch = tools.listPublicGardenTools(remaining, MAX_TAGS_PER_CONTENT);
        return build(articleBatch.items(), toolBatch.items(), articleBatch.truncated() || toolBatch.truncated());
    }

    public static Graph build(List<ArticleService.GardenArticle> articles,
                              List<ToolManagementService.GardenTool> tools) {
        return build(articles, tools, false);
    }

    static Graph build(List<ArticleService.GardenArticle> articles,
                       List<ToolManagementService.GardenTool> tools,
                       boolean sourceTruncated) {
        Map<String, CandidateNode> nodes = new LinkedHashMap<>();
        Map<EdgeKey, CandidateEdge> edges = new LinkedHashMap<>();
        boolean bounded = sourceTruncated;

        List<ArticleService.GardenArticle> articleItems = articles == null ? List.of() : articles;
        if (articleItems.size() > MAX_NODES) bounded = true;
        for (var article : articleItems.stream().sorted((left, right) -> {
            int published = comparePublishedAt(left.publishedAt(), right.publishedAt());
            return published != 0 ? published : left.slug().compareTo(right.slug());
        }).limit(MAX_NODES).toList()) {
            String articleId = nodeId(NodeType.ARTICLE, article.slug());
            if (articleId == null) continue;
            List<ArticleService.GardenTaxonomy> tags = article.tags() == null ? List.of() : article.tags();
            if (tags.size() > MAX_TAGS_PER_CONTENT) bounded = true;
            tags = tags.stream().filter(java.util.Objects::nonNull).sorted(Comparator.comparing(value -> normalizeSlug(value.slug()),
                    Comparator.nullsLast(String::compareTo))).limit(MAX_TAGS_PER_CONTENT).toList();
            addNode(nodes, new CandidateNode(articleId, NodeType.ARTICLE, cleanLabel(article.title(), article.slug()),
                    "/articles/" + article.slug(), cleanText(article.excerpt()), article.publishedAt(), 0));
            addMemberships(nodes, edges, articleId, article.category(), tags);
            addCoOccurrences(edges, tags);
        }
        int toolLimit = Math.max(0, MAX_NODES - Math.min(articleItems.size(), MAX_NODES));
        List<ToolManagementService.GardenTool> toolItems = tools == null ? List.of() : tools;
        if (toolItems.size() > toolLimit) bounded = true;
        for (var tool : toolItems.stream().sorted(Comparator.comparingInt(ToolManagementService.GardenTool::sortOrder)
                .thenComparing(ToolManagementService.GardenTool::title)
                .thenComparing(ToolManagementService.GardenTool::id)).limit(toolLimit).toList()) {
            String toolId = nodeId(NodeType.TOOL, tool.slug());
            if (toolId == null) continue;
            List<String> toolTags = tool.tags() == null ? List.of() : tool.tags();
            if (toolTags.size() > MAX_TAGS_PER_CONTENT) bounded = true;
            toolTags = toolTags.stream().sorted(String.CASE_INSENSITIVE_ORDER).limit(MAX_TAGS_PER_CONTENT).toList();
            addNode(nodes, new CandidateNode(toolId, NodeType.TOOL, cleanLabel(tool.title(), tool.slug()),
                    "/tools?tool=" + tool.slug(), cleanText(tool.description()), null, tool.sortOrder()));
            addMemberships(nodes, edges, toolId,
                    taxonomy(tool.categoryName(), tool.categorySlug()),
                    toolTags.stream()
                            .map(value -> taxonomy(value, value)).toList());
            addCoOccurrences(edges, toolTags.stream()
                    .map(value -> taxonomy(value, value)).toList());
        }

        edges.values().forEach(edge -> {
            CandidateNode source = nodes.get(edge.source());
            CandidateNode target = nodes.get(edge.target());
            if (source != null) source.fullDegree += edge.weight();
            if (target != null) target.fullDegree += edge.weight();
        });

        List<CandidateNode> orderedNodes = nodes.values().stream().sorted(NODE_ORDER).toList();
        Set<String> selectedIds = orderedNodes.stream().limit(MAX_NODES)
                .map(CandidateNode::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<CandidateEdge> orderedEdges = edges.values().stream()
                .filter(edge -> selectedIds.contains(edge.source()) && selectedIds.contains(edge.target()))
                .sorted(EDGE_ORDER)
                .toList();
        List<CandidateEdge> selectedEdges = orderedEdges.stream().limit(MAX_EDGES).toList();
        Map<String, Integer> degrees = new HashMap<>();
        for (var edge : selectedEdges) {
            degrees.merge(edge.source(), 1, Integer::sum);
            degrees.merge(edge.target(), 1, Integer::sum);
        }
        List<Node> resultNodes = orderedNodes.stream().limit(MAX_NODES)
                .map(node -> new Node(node.id(), node.type(), node.label(), node.href(), node.summary(),
                        node.publishedAt(), degrees.getOrDefault(node.id(), 0)))
                .toList();
        List<Edge> resultEdges = selectedEdges.stream()
                .map(edge -> new Edge(edge.source(), edge.target(), edge.kind(), edge.weight()))
                .toList();
        boolean truncated = bounded || resultNodes.size() < nodes.size() || resultEdges.size() < orderedEdges.size()
                || resultEdges.size() < edges.size();
        return new Graph(resultNodes, resultEdges, truncated);
    }

    private static void addMemberships(Map<String, CandidateNode> nodes, Map<EdgeKey, CandidateEdge> edges,
                                       String contentId, ArticleService.GardenTaxonomy category,
                                       List<ArticleService.GardenTaxonomy> tags) {
        if (category != null) addMembership(nodes, edges, contentId, NodeType.CATEGORY, category);
        Set<String> seen = new LinkedHashSet<>();
        for (var tag : tags == null ? List.<ArticleService.GardenTaxonomy>of() : tags) {
            if (tag != null && seen.add(normalizeSlug(tag.slug()))) {
                addMembership(nodes, edges, contentId, NodeType.TAG, tag);
            }
        }
    }

    private static void addMembership(Map<String, CandidateNode> nodes, Map<EdgeKey, CandidateEdge> edges,
                                      String contentId, NodeType type, ArticleService.GardenTaxonomy taxonomy) {
        String taxonomyId = nodeId(type, taxonomy.slug());
        if (taxonomyId == null) return;
        addNode(nodes, new CandidateNode(taxonomyId, type, cleanLabel(taxonomy.name(), taxonomy.slug()),
                taxonomyHref(type, taxonomy.slug()), null, null, 0));
        addEdge(edges, contentId, taxonomyId, EdgeKind.MEMBERSHIP, 1);
    }

    private static void addCoOccurrences(Map<EdgeKey, CandidateEdge> edges,
                                         List<? extends ArticleService.GardenTaxonomy> sourceTags) {
        List<? extends ArticleService.GardenTaxonomy> tags = sourceTags == null ? List.of() : sourceTags;
        List<String> ids = tags.stream().filter(tag -> tag != null && tag.slug() != null)
                .map(tag -> nodeId(NodeType.TAG, tag.slug())).filter(java.util.Objects::nonNull).distinct().sorted().toList();
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                addEdge(edges, ids.get(left), ids.get(right), EdgeKind.CO_OCCURRENCE, 1);
            }
        }
    }

    private static void addEdge(Map<EdgeKey, CandidateEdge> edges, String source, String target,
                                EdgeKind kind, int weight) {
        EdgeKey key = new EdgeKey(source, target, kind);
        edges.merge(key, new CandidateEdge(source, target, kind, weight),
                (left, right) -> new CandidateEdge(left.source(), left.target(), left.kind(), left.weight() + right.weight()));
    }

    private static void addNode(Map<String, CandidateNode> nodes, CandidateNode candidate) {
        nodes.merge(candidate.id(), candidate, GardenGraphService::preferNode);
    }

    private static CandidateNode preferNode(CandidateNode left, CandidateNode right) {
        String label = left.label().compareTo(right.label()) <= 0 ? left.label() : right.label();
        String summary = left.summary() != null ? left.summary() : right.summary();
        Instant publishedAt = left.publishedAt() != null ? left.publishedAt() : right.publishedAt();
        return new CandidateNode(left.id(), left.type(), label, left.href(), summary, publishedAt,
                Math.min(left.sortOrder(), right.sortOrder()));
    }

    private static final Comparator<CandidateNode> NODE_ORDER = (left, right) -> {
        int type = Integer.compare(typeOrder(left.type()), typeOrder(right.type()));
        if (type != 0) return type;
        if (left.type() == NodeType.ARTICLE) {
            int published = comparePublishedAt(left.publishedAt(), right.publishedAt());
            if (published != 0) return published;
        }
        if (left.type() == NodeType.TOOL) {
            int sort = Integer.compare(left.sortOrder(), right.sortOrder());
            if (sort != 0) return sort;
        }
        if (left.type() == NodeType.TAG || left.type() == NodeType.CATEGORY) {
            int degree = Integer.compare(right.fullDegree(), left.fullDegree());
            if (degree != 0) return degree;
        }
        return Comparator.comparing(CandidateNode::label).thenComparing(CandidateNode::id).compare(left, right);
    };

    private static final Comparator<CandidateEdge> EDGE_ORDER = Comparator
            .comparingInt(CandidateEdge::weight).reversed()
            .thenComparing(edge -> edge.kind().name())
            .thenComparing(CandidateEdge::source)
            .thenComparing(CandidateEdge::target);

    private static int typeOrder(NodeType type) {
        return switch (type) {
            case ARTICLE -> 0;
            case TOOL -> 1;
            case CATEGORY -> 2;
            case TAG -> 3;
        };
    }

    private static int comparePublishedAt(Instant left, Instant right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return right.compareTo(left);
    }

    private static String taxonomyHref(NodeType type, String slug) {
        return "/articles?" + (type == NodeType.CATEGORY ? "category" : "tag") + "=" + slug;
    }

    private static String nodeId(NodeType type, String rawSlug) {
        String slug = normalizeSlug(rawSlug);
        return slug == null ? null : type.name().toLowerCase(Locale.ROOT) + ":" + slug;
    }

    private static String normalizeSlug(String rawSlug) {
        if (rawSlug == null || rawSlug.isBlank()) return null;
        return java.text.Normalizer.normalize(rawSlug, java.text.Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanLabel(String label, String fallback) {
        return label == null || label.isBlank() ? fallback : label.trim();
    }

    private static String cleanText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ArticleService.GardenTaxonomy taxonomy(String name, String slug) {
        if (slug == null || slug.isBlank()) return null;
        return new ArticleService.GardenTaxonomy(cleanLabel(name, slug), slug);
    }

    public enum NodeType { ARTICLE, TAG, CATEGORY, TOOL }
    public enum EdgeKind { MEMBERSHIP, CO_OCCURRENCE }

    public record Node(String id, NodeType type, String label, String href, String summary,
                       Instant publishedAt, int degree) {}
    public record Edge(String source, String target, EdgeKind kind, int weight) {}
    public record Graph(List<Node> nodes, List<Edge> edges, boolean truncated) {}

    private record EdgeKey(String source, String target, EdgeKind kind) {}
    private record CandidateEdge(String source, String target, EdgeKind kind, int weight) {}
    private static final class CandidateNode {
        private final String id;
        private final NodeType type;
        private final String label;
        private final String href;
        private final String summary;
        private final Instant publishedAt;
        private final int sortOrder;
        private int fullDegree;

        private CandidateNode(String id, NodeType type, String label, String href, String summary,
                              Instant publishedAt, int sortOrder) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.href = href;
            this.summary = summary;
            this.publishedAt = publishedAt;
            this.sortOrder = sortOrder;
        }

        String id() { return id; }
        NodeType type() { return type; }
        String label() { return label; }
        String href() { return href; }
        String summary() { return summary; }
        Instant publishedAt() { return publishedAt; }
        int sortOrder() { return sortOrder; }
        int fullDegree() { return fullDegree; }
    }
}
