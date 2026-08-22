package io.haoblog.site.application;

import io.haoblog.content.application.ArticleService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PublicFeedServiceTest {
    private final SiteService siteService = mock(SiteService.class);
    private final ArticleService articleService = mock(ArticleService.class);

    @Test
    void rssIsParsedEscapedLimitedAndUsesNewestFirst() throws Exception {
        when(siteService.get()).thenReturn(new SiteService.SiteResult("Hao & <Blog>", "站点 > 描述", "https://blog.example.test", "Hao"));
        List<ArticleService.PublicFeedArticle> articles = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            articles.add(article(i, "title & <" + i + ">", "excerpt > & " + i));
        }
        when(articleService.listPublishedBatch(0, 20)).thenReturn(new ArticleService.PublishedBatch(articles, true));

        var document = new PublicFeedService(siteService, articleService).rss();
        Document xml = parse(document.body());

        assertEquals("rss", xml.getDocumentElement().getTagName());
        assertEquals(20, xml.getElementsByTagName("item").getLength());
        assertEquals("title & <0>", xml.getElementsByTagName("title").item(1).getTextContent());
        assertEquals("excerpt > & 0", xml.getElementsByTagName("description").item(1).getTextContent());
        assertTrue(document.body().contains("&amp;") && document.body().contains("&lt;"));
        assertTrue(document.body().contains("urn:haoblog:article:"));
        verify(articleService).listPublishedBatch(0, 20);
    }

    @Test
    void sitemapReadsPublishedArticlesInFiveHundredItemBatches() throws Exception {
        when(siteService.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "Night station", "https://blog.example.test", "Hao"));
        List<ArticleService.PublicFeedArticle> first = articles(500, 0);
        List<ArticleService.PublicFeedArticle> second = articles(2, 500);
        when(articleService.listPublishedBatch(0, 500)).thenReturn(new ArticleService.PublishedBatch(first, true));
        when(articleService.listPublishedBatch(1, 500)).thenReturn(new ArticleService.PublishedBatch(second, false));

        Document xml = parse(new PublicFeedService(siteService, articleService).sitemap().body());

        assertEquals(505, xml.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "url").getLength());
        verify(articleService).listPublishedBatch(eq(0), eq(500));
        verify(articleService).listPublishedBatch(eq(1), eq(500));
    }

    @Test
    void sitemapStopsAtFiftyThousandUrls() throws Exception {
        when(siteService.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "Night station", "https://blog.example.test", "Hao"));
        when(articleService.listPublishedBatch(org.mockito.ArgumentMatchers.anyInt(), eq(500)))
                .thenAnswer(invocation -> new ArticleService.PublishedBatch(articles(500, invocation.getArgument(0, Integer.class) * 500), true));

        Document xml = parse(new PublicFeedService(siteService, articleService).sitemap().body());

        assertEquals(50_000, xml.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", "url").getLength());
        verify(articleService, times(100)).listPublishedBatch(org.mockito.ArgumentMatchers.anyInt(), eq(500));
    }

    private static ArticleService.PublicFeedArticle article(int index, String title, String excerpt) {
        return new ArticleService.PublicFeedArticle(UUID.randomUUID(), "article-" + index, title, excerpt,
                Instant.parse("2026-01-" + String.format("%02d", Math.max(1, 20 - index)) + "T00:00:00Z"));
    }

    private static List<ArticleService.PublicFeedArticle> articles(int count, int offset) {
        var result = new ArrayList<ArticleService.PublicFeedArticle>(count);
        for (int i = 0; i < count; i++) result.add(article(offset + i, "Title " + (offset + i), "Excerpt"));
        return result;
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
