package io.haoblog.media.persistence;

import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    List<MediaAsset> findAllByIdInAndStatus(Collection<UUID> ids, MediaAssetStatus status);
}
