package io.haoblog.media.web;

import io.haoblog.media.application.MediaUploadService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static io.haoblog.media.web.AdminMediaDtos.*;

@RestController
@RequestMapping("/api/v1/admin/media")
@ConditionalOnProperty(prefix = "haoblog.media.cos", name = "enabled", havingValue = "true")
public class AdminMediaController {
    private final MediaUploadService service;

    public AdminMediaController(MediaUploadService service) { this.service = service; }

    @PostMapping("/uploads")
    public ResponseEntity<UploadResponse> start(@RequestBody @Valid UploadRequest request) {
        var upload = service.start(request.mimeType(), request.sizeBytes(), request.width(), request.height(), request.sha256());
        return ResponseEntity.status(201).body(UploadResponse.from(upload));
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public AssetResponse complete(@PathVariable UUID uploadId) {
        return AssetResponse.from(service.complete(uploadId));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable UUID mediaId) {
        service.delete(mediaId);
        return ResponseEntity.noContent().build();
    }
}
