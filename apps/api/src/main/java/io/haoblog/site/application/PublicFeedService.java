package io.haoblog.site.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.haoblog.content.application.ArticleService;
import io.haoblog.content.application.ArticleService.PublicFeedArticle;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

@Service
public class PublicFeedService {
    private static final int RSS_LIMIT = 20;
    private static final int SITEMAP_BATCH_SIZE = 500;
    private static final int SITEMAP_URL_LIMIT = 50_000;
    private static final int STATIC_URL_COUNT = 5;
    private static final int CACHE_MAX_BYTES = 8 * 1024 * 1024;
    private static final DateTimeFormatter RSS_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;
    private final SiteService siteService;
    private final ArticleService articleService;
    private final Cache<String, FeedDocument> cache = Caffeine.newBuilder()
            .maximumWeight(CACHE_MAX_BYTES)
            .weigher((String ignored, FeedDocument document) -> document.bytes())
            .expireAfterWrite(Duration.ofSeconds(55))
            .build();

    public PublicFeedService(SiteService siteService, ArticleService articleService) {
        this.siteService = siteService;
        this.articleService = articleService;
    }

    public FeedDocument rss() {
        return cache.get("rss", ignored -> renderRss());
    }

    public FeedDocument sitemap() {
        return cache.get("sitemap", ignored -> renderSitemap());
    }

    private FeedDocument renderRss() {
        var site = siteService.get();
        var articles = articleService.listPublishedBatch(0, RSS_LIMIT).items().stream().limit(RSS_LIMIT).toList();
        return document(writeRss(site, articles));
    }

    private FeedDocument renderSitemap() {
        var site = siteService.get();
        return document(write(writer -> {
            writer.writeStartElement("urlset");
            writer.writeDefaultNamespace("http://www.sitemaps.org/schemas/sitemap/0.9");
            for (String path : List.of("/", "/articles", "/garden", "/tools", "/about")) {
                urlElement(writer, url(site.siteUrl(), path));
            }
            int written = 0;
            int page = 0;
            int articleLimit = SITEMAP_URL_LIMIT - STATIC_URL_COUNT;
            while (written < articleLimit && page < (SITEMAP_URL_LIMIT / SITEMAP_BATCH_SIZE)) {
                var batch = articleService.listPublishedBatch(page++, SITEMAP_BATCH_SIZE);
                int remaining = articleLimit - written;
                for (var article : batch.items()) {
                    if (remaining-- <= 0) break;
                    urlElement(writer, url(site.siteUrl(), "/articles/" + article.slug()));
                    written++;
                }
                if (!batch.hasNext()) break;
            }
            writer.writeEndElement();
        }));
    }

    private static FeedDocument document(String body) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            String etag = '"' + HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8))) + '"';
            return new FeedDocument(body, etag, body.getBytes(StandardCharsets.UTF_8).length);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create feed ETag", exception);
        }
    }

    private static String writeRss(SiteService.SiteResult site, List<PublicFeedArticle> articles) {
        return write(writer -> {
            writer.writeStartElement("rss");
            writer.writeAttribute("version", "2.0");
            writer.writeStartElement("channel");
            element(writer, "title", site.title());
            element(writer, "link", url(site.siteUrl(), "/"));
            element(writer, "description", site.description());
            for (var article : articles) {
                writer.writeStartElement("item");
                element(writer, "title", article.title());
                element(writer, "description", article.excerpt());
                element(writer, "link", url(site.siteUrl(), "/articles/" + article.slug()));
                writer.writeStartElement("guid");
                writer.writeAttribute("isPermaLink", "false");
                writer.writeCharacters("urn:haoblog:article:" + article.id());
                writer.writeEndElement();
                element(writer, "pubDate", RSS_DATE_FORMAT.format(article.publishedAt().atZone(ZoneOffset.UTC)));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
        });
    }

    private static String write(WriterAction action) {
        try {
            var output = new StringWriter();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            action.write(writer);
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Unable to render XML feed", exception);
        }
    }

    private static void element(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        writer.writeStartElement(name);
        if (value != null) writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private static void urlElement(XMLStreamWriter writer, String value) throws XMLStreamException {
        writer.writeStartElement("url");
        element(writer, "loc", value);
        writer.writeEndElement();
    }

    private static String url(String siteUrl, String path) {
        return siteUrl + ("/".equals(path) ? "/" : path);
    }

    @FunctionalInterface
    private interface WriterAction {
        void write(XMLStreamWriter writer) throws XMLStreamException;
    }

    public record FeedDocument(String body, String etag, int bytes) {
        public FeedDocument(String body, String etag) {
            this(body, etag, body.getBytes(StandardCharsets.UTF_8).length);
        }
    }
}
