package io.haoblog.site.web;

import io.haoblog.site.application.PublicFeedService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class PublicFeedController {
    private static final MediaType RSS = new MediaType("application", "rss+xml", StandardCharsets.UTF_8);
    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private final PublicFeedService service;

    public PublicFeedController(PublicFeedService service) {
        this.service = service;
    }

    @GetMapping(value = "/rss.xml", produces = "application/rss+xml")
    public ResponseEntity<String> rss(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return response(service.rss(), RSS, ifNoneMatch);
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public ResponseEntity<String> sitemap(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return response(service.sitemap(), XML, ifNoneMatch);
    }

    private static ResponseEntity<String> response(PublicFeedService.FeedDocument document, MediaType contentType,
                                                   String ifNoneMatch) {
        var headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setETag(document.etag());
        headers.setCacheControl("public, max-age=0, s-maxage=0, must-revalidate");
        if (document.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(document.body());
    }
}
