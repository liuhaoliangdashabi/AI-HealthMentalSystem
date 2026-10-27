package org.example.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssConfig {

    //地域，如 cn-beijing
    private String region;

    //存储桶名
    private String bucket;

    //可选：自定义域名 / CDN 域名。留空则用阿里云默认域名
    private String domain;

    /**
     * 拼出访问前缀（不含 objectKey）
     * 用了 domain 就用 domain，否则用阿里云默认域名格式
     */
    public String buildBaseUrl() {
        if (domain != null && !domain.isBlank()) {
            return domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        }
        return "https://" + bucket + ".oss-" + region + ".aliyuncs.com";
    }
}
