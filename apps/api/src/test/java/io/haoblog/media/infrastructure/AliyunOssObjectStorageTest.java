package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunOssObjectStorageTest {
    @Test
    void policyPinsBucketObjectMimeMetadataAndExactSizeWithoutNetwork() {
        AliyunOssProperties properties = properties();
        AliyunOssObjectStorage storage = new AliyunOssObjectStorage(properties, HttpClient.newHttpClient());
        ObjectStorage.UploadGrant grant = storage.createUploadGrant(
                new ObjectStorage.UploadSpec("media/2030-01/a-safe-key.jpg", "image/jpeg", 1234, 640, 480,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                Instant.parse("2030-01-01T00:00:00Z"));

        assertTrue(grant.uploadUrl().startsWith("https://demo-123.oss-cn-guangzhou.aliyuncs.com/"));
        assertEquals("media/2030-01/a-safe-key.jpg", grant.fields().get("key"));
        assertEquals("image/jpeg", grant.fields().get("Content-Type"));
        assertEquals("204", grant.fields().get("success_action_status"));
        String policy = new String(Base64.getDecoder().decode(grant.fields().get("policy")), StandardCharsets.UTF_8);
        assertTrue(policy.contains("\"bucket\":\"demo-123\""));
        assertTrue(policy.contains("\"key\":\"media/2030-01/a-safe-key.jpg\""));
        assertTrue(policy.contains("\"Content-Type\":\"image/jpeg\""));
        assertTrue(policy.contains("[\"content-length-range\",1234,1234]"));
        assertTrue(policy.contains("\"x-oss-meta-width\":\"640\""));
        assertTrue(policy.contains("\"x-oss-meta-height\":\"480\""));
        assertEquals("OSS4-HMAC-SHA256", grant.fields().get("x-oss-signature-version"));
        assertEquals("test-id/20300101/cn-guangzhou/oss/aliyun_v4_request", grant.fields().get("x-oss-credential"));
        assertEquals(Instant.parse("2030-01-01T00:05:00Z"), grant.expiresAt());
    }

    private static AliyunOssProperties properties() {
        AliyunOssProperties properties = new AliyunOssProperties();
        properties.setBucket("demo-123");
        properties.setRegion("cn-guangzhou");
        properties.setEndpoint("https://oss-cn-guangzhou.aliyuncs.com");
        properties.setAccessKeyId("test-id");
        properties.setAccessKeySecret("server-only-secret");
        return properties;
    }
}
