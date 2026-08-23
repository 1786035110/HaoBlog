package io.haoblog.site.web;

import io.haoblog.shared.web.ProblemException;
import io.haoblog.site.application.SiteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/site")
public class AdminSiteController {
    private final SiteService service;

    public AdminSiteController(SiteService service) {
        this.service = service;
    }

    @GetMapping
    public Response get() {
        return Response.from(service.getAdmin());
    }

    @PutMapping
    public Response update(@RequestBody @Valid UpdateRequest request) {
        try {
            return Response.from(service.updateSettings(request.version(), request.commentsEnabled(),
                    request.musicEnabled(), request.threeDEnabled()));
        } catch (OptimisticLockingFailureException exception) {
            throw new ProblemException("SITE_VERSION_CONFLICT", "Site setting version conflict",
                    "Reload the latest site settings before saving", service.currentVersion());
        }
    }

    public record UpdateRequest(@NotNull Long version, @NotNull Boolean commentsEnabled,
                                Boolean musicEnabled, Boolean threeDEnabled) {}

    public record Response(String title, String description, String siteUrl, String authorName,
                           boolean commentsEnabled, boolean musicEnabled, boolean threeDEnabled, long version) {
        static Response from(SiteService.AdminSiteResult result) {
            return new Response(result.title(), result.description(), result.siteUrl(), result.authorName(),
                    result.commentsEnabled(), result.musicEnabled(), result.threeDEnabled(), result.version());
        }
    }
}
