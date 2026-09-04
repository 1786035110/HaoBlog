package io.haoblog.media.application;

import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import io.haoblog.media.infrastructure.AliyunOssProperties;
import io.haoblog.media.persistence.MediaAssetRepository;
import io.haoblog.media.persistence.MediaUpload;
import io.haoblog.media.persistence.MediaUploadRepository;
import io.haoblog.shared.web.ProblemException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "haoblog.media.oss", name = "enabled", havingValue = "true")
public class MediaUploadService {
    private static final Set<String> MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private final MediaUploadRepository uploads;
    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final MediaReferenceQuery references;
    private final AliyunOssProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MediaUploadService(MediaUploadRepository uploads, MediaAssetRepository assets,
                              ObjectStorage storage, MediaReferenceQuery references,
                              AliyunOssProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.uploads = uploads;
        this.assets = assets;
        this.storage = storage;
        this.references = references;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public UploadStarted start(String mimeType, long sizeBytes, int width, int height, String sha256) {
        validate(mimeType, sizeBytes, width, height, sha256);
        Instant now = Instant.now(clock);
        UUID id = UUID.randomUUID();
        String extension = mimeType.substring("image/".length()).replace("jpeg", "jpg");
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        String objectKey = "media/" + YearMonth.from(now.atZone(clock.getZone())) + "/" + random + "." + extension;
        Instant expiresAt = now.plusSeconds(300);
        ObjectStorage.UploadSpec spec = new ObjectStorage.UploadSpec(objectKey, mimeType, sizeBytes,
                width, height, sha256);
        ObjectStorage.UploadGrant grant;
        try {
            grant = storage.createUploadGrant(spec, now);
        } catch (ObjectStorageException exception) {
            throw storageUnavailable();
        }
        uploads.save(new MediaUpload(id, objectKey, mimeType, sizeBytes, width, height, sha256, expiresAt, now));
        return new UploadStarted(id, objectKey, grant.uploadUrl(), grant.fields(), grant.expiresAt());
    }

    @Transactional(propagation = Propagation.NEVER)
    public MediaAsset complete(UUID uploadId) {
        MediaUpload upload = transaction.execute(status -> uploads.findByIdForUpdate(uploadId)
                .orElseThrow(() -> problem("MEDIA_UPLOAD_NOT_FOUND", "Upload intent not found", "The upload intent does not exist")));
        if (upload.getCompletedMediaId() != null) return completed(upload);
        checkExpiry(upload);
        ObjectStorage.StoredObject stored;
        try {
            stored = storage.head(upload.getObjectKey());
        } catch (ObjectStorageException exception) {
            throw storageUnavailable();
        }
        return transaction.execute(status -> finish(uploadId, stored));
    }

    private MediaAsset completed(MediaUpload upload) {
        return assets.findById(upload.getCompletedMediaId())
                .orElseThrow(() -> problem("MEDIA_ASSET_NOT_FOUND", "Media asset not found", "The completed media asset is unavailable"));
    }

    private void checkExpiry(MediaUpload upload) {
        if (!clock.instant().isBefore(upload.getExpiresAt())) {
            throw problem("MEDIA_UPLOAD_EXPIRED", "Upload intent expired", "Request a new upload intent");
        }
    }

    private MediaAsset finish(UUID uploadId, ObjectStorage.StoredObject stored) {
        MediaUpload upload = uploads.findByIdForUpdate(uploadId)
                .orElseThrow(() -> problem("MEDIA_UPLOAD_NOT_FOUND", "Upload intent not found", "The upload intent does not exist"));
        if (upload.getCompletedMediaId() != null) return completed(upload);
        checkExpiry(upload);
        Instant now = Instant.now(clock);
        if (stored == null) {
            throw problem("MEDIA_OBJECT_NOT_FOUND", "Uploaded object not found", "Upload the object before confirming it");
        }
        if (!upload.getObjectKey().equals(stored.objectKey())
                || !upload.getMimeType().equalsIgnoreCase(stored.mimeType())
                || upload.getSizeBytes() != stored.sizeBytes()
                || stored.width() == null || upload.getWidth() != stored.width()
                || stored.height() == null || upload.getHeight() != stored.height()
                || !upload.getSha256().equalsIgnoreCase(stored.sha256())) {
            throw problem("MEDIA_METADATA_MISMATCH", "Uploaded metadata mismatch", "The object metadata does not match the signed upload intent");
        }
        MediaAsset asset = new MediaAsset(upload.getObjectKey(), storage.publicUrl(upload.getObjectKey()),
                upload.getMimeType(), upload.getSizeBytes(), upload.getWidth(), upload.getHeight(),
                HexFormat.of().parseHex(upload.getSha256()), MediaAssetStatus.AVAILABLE, now);
        MediaAsset saved = assets.saveAndFlush(asset);
        upload.complete(saved.getId());
        return saved;
    }

    @Transactional
    public void delete(UUID mediaId) {
        MediaAsset asset = assets.findById(mediaId)
                .orElseThrow(() -> problem("MEDIA_ASSET_NOT_FOUND", "Media asset not found", "The media asset does not exist"));
        if (asset.getStatus() == MediaAssetStatus.DELETED) return;
        if (references.isReferenced(asset.getId(), asset.getPublicUrl())) {
            throw problem("MEDIA_IN_USE", "Media asset is referenced", "Remove the cover or Markdown reference before recycling this media");
        }
        asset.markDeleted(Instant.now(clock));
        assets.saveAndFlush(asset);
    }

    private void validate(String mimeType, long sizeBytes, int width, int height, String sha256) {
        if (!MIME_TYPES.contains(mimeType) || sizeBytes < 1 || sizeBytes > properties.getMaxSizeBytes()
                || width < 1 || height < 1 || width > properties.getMaxDimension() || height > properties.getMaxDimension()
                || sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw problem("MEDIA_UPLOAD_INVALID", "Invalid upload metadata", "MIME, size, dimensions or SHA-256 is invalid");
        }
    }

    private static ProblemException problem(String code, String title, String detail) {
        return new ProblemException(code, title, detail);
    }

    private static ProblemException storageUnavailable() {
        return problem("MEDIA_STORAGE_UNAVAILABLE", "Media storage unavailable", "The object storage metadata check could not be completed");
    }

    public record UploadStarted(UUID uploadId, String objectKey, String uploadUrl,
                                Map<String, String> fields, Instant expiresAt) {}
}
