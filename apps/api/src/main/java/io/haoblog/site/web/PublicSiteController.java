package io.haoblog.site.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/site")
public class PublicSiteController {
    @GetMapping
    public SiteResponse site() {
        return new SiteResponse("HaoBlog", "极夜观测站");
    }

    public record SiteResponse(String title, String description) {}
}
