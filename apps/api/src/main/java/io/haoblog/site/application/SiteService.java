package io.haoblog.site.application;

import io.haoblog.site.persistence.SiteSettingRepository;
import org.springframework.stereotype.Service;

@Service
public class SiteService {
    private final SiteSettingRepository repository;
    public SiteService(SiteSettingRepository repository) { this.repository = repository; }
    public SiteResult get() { var setting = repository.findBySiteKey("default").orElseThrow(); return new SiteResult(setting.getTitle(), setting.getDescription()); }
    public record SiteResult(String title, String description) {}
}
