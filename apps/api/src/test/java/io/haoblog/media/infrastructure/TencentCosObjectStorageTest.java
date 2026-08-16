package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentCosObjectStorageTest {
    @Test
    void policyPinsBucketObjectMimeMetadataAndExactSizeWithoutNetwork() {
        TencentCosProperties properties = properties();
        TencentCosObjectStorage storage = new TencentCosObjectStorage(properties, HttpClient.newHttpClient(),
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        ObjectStorage.UploadGrant grant = storage.createUploadGrant(
                new ObjectStorage.UploadSpec("media/2030-01/a-safe-key.jpg", "image/jpeg", 1234, 640, 480,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                Instant.parse("2030-01-01T00:00:00Z"));

        assertTrue(grant.uploadUrl().startsWith("https://demo-123.cos.ap-shanghai.myqcloud.com/"));
        assertEquals("media/2030-01/a-safe-key.jpg", grant.fields().get("key"));
        assertEquals("image/jpeg", grant.fields().get("Content-Type"));
        assertEquals("204", grant.fields().get("success_action_status"));
        String policy = new String(Base64.getDecoder().decode(grant.fields().get("policy")), StandardCharsets.UTF_8);
        assertTrue(policy.contains("\"bucket\":\"demo-123\""));
        assertTrue(policy.contains("\"key\":\"media/2030-01/a-safe-key.jpg\""));
        assertTrue(policy.contains("\"Content-Type\":\"image/jpeg\""));
        assertTrue(policy.contains("[\"content-length-range\",1234,1234]"));
        assertTrue(policy.contains("\"x-cos-meta-width\":\"640\""));
        assertTrue(policy.contains("\"x-cos-meta-height\":\"480\""));
        assertEquals(Instant.parse("2030-01-01T00:05:00Z"), grant.expiresAt());
    }

    private static TencentCosProperties properties() {
        TencentCosProperties properties = new TencentCosProperties();
        properties.setBucket("demo-123");
        properties.setRegion("ap-shanghai");
        properties.setSecretId("secret-id");
        properties.setSecretKey("server-only-secret");
        return properties;
    }
}
