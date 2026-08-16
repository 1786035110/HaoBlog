package io.haoblog.media.web;

import io.haoblog.media.application.MediaUploadService;
import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AdminMediaDtos {
    private AdminMediaDtos() {}

    public record UploadRequest(@NotBlank String mimeType, @NotNull @Positive Long sizeBytes,
                                @NotNull @Positive Integer width, @NotNull @Positive Integer height,
                                @NotBlank String sha256) {}

    public record UploadResponse(UUID uploadId, String objectKey, String uploadUrl,
                                 Map<String, String> fields, Instant expiresAt) {
        static UploadResponse from(MediaUploadService.UploadStarted upload) {
            return new UploadResponse(upload.uploadId(), upload.objectKey(), upload.uploadUrl(), upload.fields(), upload.expiresAt());
        }
    }

    public record AssetResponse(UUID id, String objectKey, String publicUrl, String mimeType,
                                long sizeBytes, Integer width, Integer height, String sha256,
                                MediaAssetStatus status, Instant createdAt, Instant updatedAt) {
        static AssetResponse from(MediaAsset asset) {
            return new AssetResponse(asset.getId(), asset.getObjectKey(), asset.getPublicUrl(), asset.getMimeType(),
                    asset.getSizeBytes(), asset.getWidth(), asset.getHeight(), java.util.HexFormat.of().formatHex(asset.getSha256()),
                    asset.getStatus(), asset.getCreatedAt(), asset.getUpdatedAt());
        }
    }
}
