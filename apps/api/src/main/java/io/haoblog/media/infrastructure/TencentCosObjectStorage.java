package io.haoblog.media.infrastructure;

import io.haoblog.media.application.ObjectStorage;
import io.haoblog.media.application.ObjectStorageException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 腾讯 COS POST policy 与 HEAD Object 的单一适配器，不代理上传内容。 */
public class TencentCosObjectStorage implements ObjectStorage {
    private final TencentCosProperties properties;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Semaphore headSlots = new Semaphore(4);
    private final String host;
    private final String publicBaseUrl;

    public TencentCosObjectStorage(TencentCosProperties properties, HttpClient httpClient) {
        this(properties, httpClient, Clock.systemUTC());
    }

    TencentCosObjectStorage(TencentCosProperties properties, HttpClient httpClient, Clock clock) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.clock = clock;
        require(properties.getBucket(), "bucket");
        require(properties.getRegion(), "region");
        require(properties.getSecretId(), "secretId");
        require(properties.getSecretKey(), "secretKey");
        if (properties.getMaxSizeBytes() <= 0 || properties.getMaxDimension() <= 0) {
            throw new IllegalArgumentException("COS upload limits must be positive");
        }
        host = properties.getBucket() + ".cos." + properties.getRegion() + ".myqcloud.com";
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
        long start = now.getEpochSecond() - 30;
        long end = now.plusSeconds(300).getEpochSecond();
        String keyTime = start + ";" + end;
        String policyJson = policy(spec, keyTime, Instant.ofEpochSecond(end));
        String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signKey = hmacHex(properties.getSecretKey(), keyTime);
        String signature = hmacHex(signKey, sha1Hex(policyJson));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", spec.objectKey());
        fields.put("Content-Type", spec.mimeType());
        fields.put("x-cos-meta-sha256", spec.sha256());
        fields.put("x-cos-meta-width", Integer.toString(spec.width()));
        fields.put("x-cos-meta-height", Integer.toString(spec.height()));
        fields.put("success_action_status", "204");
        fields.put("policy", policy);
        fields.put("q-sign-algorithm", "sha1");
        fields.put("q-ak", properties.getSecretId());
        fields.put("q-key-time", keyTime);
        fields.put("q-signature", signature);
        return new UploadGrant("https://" + host + "/", fields, Instant.ofEpochSecond(end));
    }

    @Override
    public StoredObject head(String objectKey) {
        if (!headSlots.tryAcquire()) throw new ObjectStorageException("COS metadata capacity is temporarily exhausted");
        try {
            Instant now = Instant.now(clock);
            long start = now.getEpochSecond() - 5;
            long end = now.plusSeconds(60).getEpochSecond();
            String keyTime = start + ";" + end;
            String path = "/" + objectKey;
            String headers = "host=" + percentEncode(host);
            String httpString = "head\n" + path + "\n\n" + headers + "\n";
            String signKey = hmacHex(properties.getSecretKey(), keyTime);
            String stringToSign = "sha1\n" + keyTime + "\n" + sha1Hex(httpString) + "\n";
            String authorization = "q-sign-algorithm=sha1&q-ak=" + properties.getSecretId()
                    + "&q-sign-time=" + keyTime + "&q-key-time=" + keyTime
                    + "&q-header-list=host&q-url-param-list=&q-signature="
                    + hmacHex(signKey, stringToSign);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", authorization)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ObjectStorageException("COS metadata request failed: " + response.statusCode());
            }
            return new StoredObject(objectKey, response.headers().firstValue("content-type").orElse(""),
                    response.headers().firstValueAsLong("content-length").orElse(-1),
                    parseInteger(response.headers().firstValue("x-cos-meta-width").orElse(null)),
                    parseInteger(response.headers().firstValue("x-cos-meta-height").orElse(null)),
                    response.headers().firstValue("x-cos-meta-sha256").orElse(""));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ObjectStorageException("COS metadata request interrupted", exception);
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("COS metadata request failed", exception);
        } finally {
            headSlots.release();
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        return publicBaseUrl + "/" + objectKey;
    }

    private String policy(UploadSpec spec, String keyTime, Instant expiresAt) {
        return "{\"expiration\":\"" + expiresAt + "\",\"conditions\":["
                + "{\"bucket\":\"" + json(properties.getBucket()) + "\"},"
                + "{\"key\":\"" + json(spec.objectKey()) + "\"},"
                + "{\"Content-Type\":\"" + json(spec.mimeType()) + "\"},"
                + "{\"x-cos-meta-sha256\":\"" + spec.sha256() + "\"},"
                + "{\"x-cos-meta-width\":\"" + spec.width() + "\"},"
                + "{\"x-cos-meta-height\":\"" + spec.height() + "\"},"
                + "{\"success_action_status\":\"204\"},"
                + "[\"content-length-range\"," + spec.sizeBytes() + "," + spec.sizeBytes() + "],"
                + "{\"q-sign-algorithm\":\"sha1\"},{\"q-ak\":\"" + json(properties.getSecretId())
                + "\"},{\"q-sign-time\":\"" + keyTime + "\"}]}";
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("COS " + name + " is required");
    }

    private static String trimTrailingSlash(String value) { return value.replaceAll("/+$", ""); }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String sha1Hex(String value) { return digest("SHA-1", value.getBytes(StandardCharsets.UTF_8)); }

    private static String hmacHex(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate COS signature", exception);
        }
    }

    private static String digest(String algorithm, byte[] value) {
        try { return hex(MessageDigest.getInstance(algorithm).digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("Unable to calculate digest", exception); }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static String percentEncode(String value) {
        StringBuilder result = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int c = item & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-_.~".indexOf(c) >= 0) {
                result.append((char) c);
            } else result.append('%').append(String.format("%02X", c));
        }
        return result.toString();
    }
}
