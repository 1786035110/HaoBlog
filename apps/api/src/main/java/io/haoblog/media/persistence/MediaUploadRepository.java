package io.haoblog.media.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select upload from MediaUpload upload where upload.id = :id")
    Optional<MediaUpload> findByIdForUpdate(@Param("id") UUID id);
}
