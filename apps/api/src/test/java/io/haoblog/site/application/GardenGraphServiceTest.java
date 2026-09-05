package io.haoblog.site.application;

import io.haoblog.content.application.ArticleService;
import io.haoblog.toolbox.application.ToolManagementService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GardenGraphServiceTest {
    @Test
    void normalizesTagsBuildsWeightedCoOccurrenceAndKeepsToolHrefInternal() {
        var article = new ArticleService.GardenArticle(UUID.randomUUID(), "first-post", "First post", "Excerpt",
                Instant.parse("2026-01-02T00:00:00Z"),
                new ArticleService.GardenTaxonomy("Backend", "backend"),
                List.of(new ArticleService.GardenTaxonomy("Java", "Java"), new ArticleService.GardenTaxonomy("Spring", "spring")));
        var tool = new ToolManagementService.GardenTool(UUID.randomUUID(), "JSON Tool", "json-tool", "Format JSON",
                "Utilities", "utilities", List.of("java", "spring"), 1);

        var graph = GardenGraphService.build(List.of(article), List.of(tool));

        assertEquals(6, graph.nodes().size());
        assertTrue(graph.nodes().stream().anyMatch(node -> node.id().equals("tool:json-tool")
                && node.href().equals("/tools?tool=json-tool")));
        assertEquals(2, graph.edges().stream()
                .filter(edge -> edge.kind() == GardenGraphService.EdgeKind.CO_OCCURRENCE)
                .findFirst().orElseThrow().weight());
        assertEquals(graph, GardenGraphService.build(List.of(article), List.of(tool)));
    }

    @Test
    void deterministicallyClipsNodesAndEdges() {
        var articles = java.util.stream.IntStream.range(0, 205).mapToObj(index ->
                new ArticleService.GardenArticle(UUID.randomUUID(), "post-" + index, "Post " + index, null,
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index), null, List.of())).toList();
        var clippedNodes = GardenGraphService.build(articles, List.of());
        assertEquals(200, clippedNodes.nodes().size());
        assertTrue(clippedNodes.truncated());
        assertEquals("article:post-204", clippedNodes.nodes().get(0).id());

        var sharedTags = java.util.stream.IntStream.range(0, 100).mapToObj(index ->
                new ArticleService.GardenArticle(UUID.randomUUID(), "dense-" + index, "Dense " + index, null,
                        Instant.parse("2026-01-01T00:00:00Z"), null,
                        java.util.stream.IntStream.range(0, 6)
                                .mapToObj(tag -> new ArticleService.GardenTaxonomy("Tag " + tag, "tag-" + tag)).toList())).toList();
        var clippedEdges = GardenGraphService.build(sharedTags, List.of());
        assertEquals(600, clippedEdges.edges().size());
        assertTrue(clippedEdges.truncated());
    }

    @Test
    void boundsPerContentTagsBeforeBuildingRelationships() {
        var article = new ArticleService.GardenArticle(UUID.randomUUID(), "bounded", "Bounded", null,
                Instant.parse("2026-01-01T00:00:00Z"), null,
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> new ArticleService.GardenTaxonomy("Tag " + index, "tag-" + index)).toList());

        var graph = GardenGraphService.build(List.of(article), List.of());

        assertEquals(13, graph.nodes().size());
        assertEquals(78, graph.edges().size());
        assertTrue(graph.truncated());
        assertTrue(graph.nodes().stream().noneMatch(node -> node.id().equals("tag:tag-9")));
    }
}
