package io.haoblog.media.application;

import java.time.Instant;
import java.util.Map;

/** media 模块唯一的对象存储边界；调用方永远不会接触厂商客户端。 */
public interface ObjectStorage {
    UploadGrant createUploadGrant(UploadSpec spec, Instant now);

    StoredObject head(String objectKey);

    String publicUrl(String objectKey);

    record UploadSpec(String objectKey, String mimeType, long sizeBytes, int width, int height, String sha256) {}

    record UploadGrant(String uploadUrl, Map<String, String> fields, Instant expiresAt) {}

    record StoredObject(String objectKey, String mimeType, long sizeBytes,
                        Integer width, Integer height, String sha256) {}
}
