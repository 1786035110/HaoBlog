package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void dataRequestsUseTheConfiguredCustomDomain() throws Exception {
        AliyunOssProperties properties = properties();
        properties.setPublicBaseUrl("https://img.haoblog.com.cn/");
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        AliyunOssObjectStorage storage = new AliyunOssObjectStorage(properties, client);

        ObjectStorage.UploadGrant grant = storage.createUploadGrant(
                new ObjectStorage.UploadSpec("media/2030-01/a-safe-key.jpg", "image/jpeg", 1234, 640, 480,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                Instant.parse("2030-01-01T00:00:00Z"));

        assertEquals("https://img.haoblog.com.cn/", grant.uploadUrl());
        assertEquals("https://img.haoblog.com.cn/media/2030-01/a-safe-key.jpg",
                storage.publicUrl("media/2030-01/a-safe-key.jpg"));
        assertNull(storage.head("media/2030-01/a safe-key.jpg"));
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(URI.create("https://img.haoblog.com.cn/media/2030-01/a%20safe-key.jpg"), request.getValue().uri());
    }

    @Test
    void rejectsUnsafeOrNonOriginPublicDomains() {
        for (String value : List.of(
                "http://img.haoblog.com.cn",
                "https://user:pass@img.haoblog.com.cn",
                "https://img.haoblog.com.cn/media",
                "https://img.haoblog.com.cn?version=1",
                "https://img.haoblog.com.cn#objects")) {
            AliyunOssProperties properties = properties();
            properties.setPublicBaseUrl(value);
            assertThrows(IllegalStateException.class,
                    () -> new AliyunOssObjectStorage(properties, HttpClient.newHttpClient()));
        }
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
