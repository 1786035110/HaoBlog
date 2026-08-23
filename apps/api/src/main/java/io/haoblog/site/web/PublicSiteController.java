package io.haoblog.site.web;

import io.haoblog.site.application.SiteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/public/site")
public class PublicSiteController {
    private final SiteService service;
    public PublicSiteController(SiteService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<SiteResponse> site(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var result = service.get();
        var response = new SiteResponse(result.title(), result.description(), result.siteUrl(), result.authorName(),
                result.commentsEnabled(), result.musicEnabled(), result.threeDEnabled(), result.musicManifestUrl());
        var headers = new HttpHeaders();
        headers.setETag(representationHash(response));
        headers.setCacheControl("public, max-age=0, s-maxage=60, must-revalidate");
        if (headers.getETag().equals(ifNoneMatch)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(response);
    }

    private static String representationHash(Object value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return '"' + HexFormat.of().formatHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create site representation ETag", exception);
        }
    }

    public record SiteResponse(String title, String description, String siteUrl, String authorName,
                               boolean commentsEnabled, boolean musicEnabled, boolean threeDEnabled,
                               String musicManifestUrl) {
        public SiteResponse(String title, String description, String siteUrl, String authorName) {
            this(title, description, siteUrl, authorName, true, false, false, null);
        }
        public SiteResponse(String title, String description, String siteUrl, String authorName,
                            boolean commentsEnabled) {
            this(title, description, siteUrl, authorName, commentsEnabled, false, false, null);
        }
    }
}
