package io.haoblog.site.web;

import io.haoblog.site.application.SiteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/site")
public class PublicSiteController {
    private final SiteService service;
    public PublicSiteController(SiteService service) { this.service = service; }
    @GetMapping
    public SiteResponse site() {
        var result = service.get();
        return new SiteResponse(result.title(), result.description());
    }

    public record SiteResponse(String title, String description) {}
}
