package io.haoblog.media.application;

import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import io.haoblog.media.infrastructure.TencentCosProperties;
import io.haoblog.media.persistence.MediaAssetRepository;
import io.haoblog.media.persistence.MediaUpload;
import io.haoblog.media.persistence.MediaUploadRepository;
import io.haoblog.shared.web.ProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaUploadServiceTest {
    private final MediaUploadRepository uploads = mock(MediaUploadRepository.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MediaReferenceQuery references = mock(MediaReferenceQuery.class);
    private final TencentCosProperties properties = new TencentCosProperties();
    private MediaUploadService service;
    private final Instant now = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        properties.setMaxSizeBytes(5 * 1024 * 1024L);
        properties.setMaxDimension(2560);
        service = new MediaUploadService(uploads, assets, storage, references,
                properties, Clock.fixed(now, ZoneOffset.UTC));
        when(storage.createUploadGrant(any(), any())).thenAnswer(invocation -> {
            ObjectStorage.UploadSpec spec = invocation.getArgument(0);
            return new ObjectStorage.UploadGrant("https://mock.invalid/", Map.of("key", spec.objectKey()), now.plusSeconds(300));
        });
    }

    @Test
    void startValidatesBoundaryAndGeneratesServerOwnedRandomKey() {
        MediaUploadService.UploadStarted started = service.start("image/jpeg", 1000, 640, 480, sha());
        assertEquals(started.objectKey(), started.fields().get("key"));
        assertEquals("media/2030-01/", started.objectKey().substring(0, "media/2030-01/".length()));
        assertFalse(started.objectKey().contains("client"));
        verify(uploads).save(any(MediaUpload.class));
        assertThrows(ProblemException.class, () -> service.start("image/svg+xml", 1000, 10, 10, sha()));
        assertThrows(ProblemException.class, () -> service.start("image/jpeg", 6 * 1024 * 1024L, 10, 10, sha()));
    }

    @Test
    void forgedOrMismatchedCompletionNeverCreatesMediaAsset() {
        UUID uploadId = UUID.randomUUID();
        MediaUpload upload = new MediaUpload(uploadId, "media/2030-01/key.jpg", "image/jpeg", 1000, 640, 480, sha(), now.plusSeconds(300), now);
        when(uploads.findByIdForUpdate(uploadId)).thenReturn(Optional.of(upload));
        when(storage.head(upload.getObjectKey())).thenReturn(new ObjectStorage.StoredObject(upload.getObjectKey(), "image/png", 1000, 640, 480, sha()));
        ProblemException exception = assertThrows(ProblemException.class, () -> service.complete(uploadId));
        assertEquals("MEDIA_METADATA_MISMATCH", exception.getCode());
        verify(assets, never()).saveAndFlush(any());
    }

    @Test
    void successfulCompletionCreatesAvailableAssetAndIsIdempotent() {
        UUID uploadId = UUID.randomUUID();
        MediaUpload upload = new MediaUpload(uploadId, "media/2030-01/key.jpg", "image/jpeg", 1000, 640, 480, sha(), now.plusSeconds(300), now);
        when(uploads.findByIdForUpdate(uploadId)).thenReturn(Optional.of(upload));
        when(storage.head(upload.getObjectKey())).thenReturn(new ObjectStorage.StoredObject(upload.getObjectKey(), "image/jpeg", 1000, 640, 480, sha()));
        when(storage.publicUrl(upload.getObjectKey())).thenReturn("https://cdn.invalid/key.jpg");
        when(assets.saveAndFlush(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MediaAsset created = service.complete(uploadId);
        assertEquals(MediaAssetStatus.AVAILABLE, created.getStatus());
        assertEquals(upload.getObjectKey(), created.getObjectKey());
        assertEquals(created.getId(), upload.getCompletedMediaId());
        when(assets.findById(created.getId())).thenReturn(Optional.of(created));
        assertEquals(created, service.complete(uploadId));
        verify(storage, times(1)).head(upload.getObjectKey());
    }

    @Test
    void referencedAssetIsRecycledOnlyWhenUnreferenced() {
        byte[] hash = new byte[32];
        MediaAsset asset = new MediaAsset("media/2030-01/key.jpg", "https://cdn.invalid/key.jpg", "image/jpeg", 1000, 640, 480, hash, MediaAssetStatus.AVAILABLE, now);
        when(assets.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(references.isReferenced(asset.getId(), asset.getPublicUrl())).thenReturn(true);
        ProblemException exception = assertThrows(ProblemException.class, () -> service.delete(asset.getId()));
        assertEquals("MEDIA_IN_USE", exception.getCode());
        verify(assets, never()).saveAndFlush(any());
        when(references.isReferenced(asset.getId(), asset.getPublicUrl())).thenReturn(false);
        service.delete(asset.getId());
        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        verify(assets).saveAndFlush(asset);
    }

    private static String sha() { return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"; }
}
