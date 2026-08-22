package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import io.haoblog.media.application.ObjectStorageException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 阿里云 OSS V4 POST policy 与公开对象 HEAD 的适配器，不代理上传内容。 */
public class AliyunOssObjectStorage implements ObjectStorage {
    private static final String TERMINATOR = "aliyun_v4_request";
    private static final String SIGNATURE_VERSION = "OSS4-HMAC-SHA256";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final AliyunOssProperties properties;
    private final HttpClient httpClient;
    private final Semaphore headSlots = new Semaphore(4);
    private final String host;
    private final String publicBaseUrl;

    public AliyunOssObjectStorage(AliyunOssProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
        require(properties.getBucket(), "bucket");
        require(properties.getRegion(), "region");
        require(properties.getEndpoint(), "endpoint");
        require(properties.getAccessKeyId(), "accessKeyId");
        require(properties.getAccessKeySecret(), "accessKeySecret");
        if (properties.getMaxSizeBytes() <= 0 || properties.getMaxDimension() <= 0) {
            throw new IllegalArgumentException("OSS upload limits must be positive");
        }
        host = properties.getBucket() + "." + endpointHost(properties.getEndpoint());
        publicBaseUrl = trimTrailingSlash(properties.getPublicBaseUrl().isBlank()
                ? "https://" + host : properties.getPublicBaseUrl());
    }

    @Override
    public UploadGrant createUploadGrant(UploadSpec spec, Instant now) {
        if (spec.sizeBytes() < 1 || spec.sizeBytes() > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException("upload size is outside the configured limit");
        }
        if (spec.width() < 1 || spec.height() < 1
                || spec.width() > properties.getMaxDimension() || spec.height() > properties.getMaxDimension()) {
            throw new IllegalArgumentException("upload dimensions are outside the configured limit");
        }
        String date = DATE.format(now);
        String dateTime = DATE_TIME.format(now);
        String credential = properties.getAccessKeyId() + "/" + date + "/" + properties.getRegion() + "/oss/" + TERMINATOR;
        Instant expiresAt = now.plusSeconds(300);
        String policyJson = policy(spec, expiresAt, credential, dateTime);
        String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signature = hex(hmac(signingKey(date), policy));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", spec.objectKey());
        fields.put("Content-Type", spec.mimeType());
        fields.put("x-oss-meta-sha256", spec.sha256());
        fields.put("x-oss-meta-width", Integer.toString(spec.width()));
        fields.put("x-oss-meta-height", Integer.toString(spec.height()));
        fields.put("success_action_status", "204");
        fields.put("policy", policy);
        fields.put("x-oss-signature-version", SIGNATURE_VERSION);
        fields.put("x-oss-credential", credential);
        fields.put("x-oss-date", dateTime);
        fields.put("x-oss-signature", signature);
        return new UploadGrant("https://" + host + "/", fields, expiresAt);
    }

    @Override
    public StoredObject head(String objectKey) {
        if (!headSlots.tryAcquire()) throw new ObjectStorageException("OSS metadata capacity is temporarily exhausted");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + "/" + encodePath(objectKey)))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ObjectStorageException("OSS metadata request failed: " + response.statusCode());
            }
            return new StoredObject(objectKey, response.headers().firstValue("content-type").orElse(""),
                    response.headers().firstValueAsLong("content-length").orElse(-1),
                    parseInteger(response.headers().firstValue("x-oss-meta-width").orElse(null)),
                    parseInteger(response.headers().firstValue("x-oss-meta-height").orElse(null)),
                    response.headers().firstValue("x-oss-meta-sha256").orElse(""));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ObjectStorageException("OSS metadata request interrupted", exception);
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("OSS metadata request failed", exception);
        } finally {
            headSlots.release();
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    private String policy(UploadSpec spec, Instant expiresAt, String credential, String dateTime) {
        return "{\"expiration\":\"" + expiresAt + "\",\"conditions\":["
                + "{\"bucket\":\"" + json(properties.getBucket()) + "\"},"
                + "{\"key\":\"" + json(spec.objectKey()) + "\"},"
                + "{\"Content-Type\":\"" + json(spec.mimeType()) + "\"},"
                + "{\"x-oss-meta-sha256\":\"" + spec.sha256() + "\"},"
                + "{\"x-oss-meta-width\":\"" + spec.width() + "\"},"
                + "{\"x-oss-meta-height\":\"" + spec.height() + "\"},"
                + "{\"success_action_status\":\"204\"},"
                + "{\"x-oss-signature-version\":\"OSS4-HMAC-SHA256\"},"
                + "{\"x-oss-credential\":\"" + json(credential) + "\"},"
                + "{\"x-oss-date\":\"" + dateTime + "\"},"
                + "[\"content-length-range\"," + spec.sizeBytes() + "," + spec.sizeBytes() + "]]}";
    }

    private byte[] signingKey(String date) {
        byte[] dateKey = hmac(("aliyun_v4" + properties.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, properties.getRegion());
        byte[] serviceKey = hmac(regionKey, "oss");
        return hmac(serviceKey, TERMINATOR);
    }

    private static byte[] hmac(byte[] key, String value) {
        return hmac(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate OSS signature", exception);
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("OSS " + name + " is required");
    }

    private static String endpointHost(String endpoint) {
        URI uri = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalStateException("OSS endpoint is invalid");
        return uri.getHost();
    }

    private static String trimTrailingSlash(String value) { return value.replaceAll("/+$", ""); }

    private static String encodePath(String value) {
        StringBuilder result = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int c = item & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-_.~/".indexOf(c) >= 0) {
                result.append((char) c);
            } else result.append('%').append(String.format("%02X", c));
        }
        return result.toString();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
