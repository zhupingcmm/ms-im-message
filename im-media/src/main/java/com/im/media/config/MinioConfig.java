package com.im.media.config;

import io.minio.MinioClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class MinioConfig {

    @Value("${im.media.endpoint:http://8.153.38.116:9000}")
    private String endpoint;
    @Value("${im.media.access-key:minioadmin}")
    private String accessKey;
    @Value("${im.media.secret-key:minioadmin}")
    private String secretKey;
    @Value("${im.media.bucket:im-media}")
    private String bucket;
    /** 预签名 URL 有效期（秒）。 */
    @Value("${im.media.presign-expiry-seconds:600}")
    private int presignExpirySeconds;
    /** 对外可访问的下载基址（CDN/反代）；为空时回退到 endpoint。 */
    @Value("${im.media.public-base-url:}")
    private String publicBaseUrl;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
