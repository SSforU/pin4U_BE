package io.github.ssforu.pin4u.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.s3")
public class UploadProps {
    private String bucket;
    private String region;
    private String publicPrefix; // ← camelCase 로 변경 (binding은 public_prefix/public-prefix 모두 허용)
    private String endpoint;

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getPublicPrefix() { return publicPrefix; }
    public void setPublicPrefix(String publicPrefix) { this.publicPrefix = publicPrefix; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String toPublicUrl(String key) {
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint.endsWith("/") ? endpoint + key : endpoint + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }
}
