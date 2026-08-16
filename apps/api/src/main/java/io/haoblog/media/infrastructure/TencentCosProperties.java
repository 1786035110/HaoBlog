package io.haoblog.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "haoblog.media.cos")
public class TencentCosProperties {
    private boolean enabled;
    private String bucket = "";
    private String region = "";
    private String secretId = "";
    private String secretKey = "";
    private String publicBaseUrl = "";
    private long maxSizeBytes = 5L * 1024 * 1024;
    private int maxDimension = 2560;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSecretId() { return secretId; }
    public void setSecretId(String secretId) { this.secretId = secretId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
    public int getMaxDimension() { return maxDimension; }
    public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }
}
