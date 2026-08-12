package io.haoblog.site.persistence;

import io.haoblog.site.domain.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, UUID> {
    Optional<SiteSetting> findBySiteKey(String siteKey);
}
