package com.im.media.service;

import com.im.media.config.MinioConfig;
import com.im.media.dto.PresignRequest;
import com.im.media.dto.PresignResponse;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 预签名直传：服务端只签发上传 URL，文件本体由客户端直传对象存储，不经业务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MinioClient minioClient;
    private final MinioConfig config;

    /** 为指定用户签发一个上传预签名（对象 key 隔离到 {userId}/{date}/{uuid.ext}）。 */
    public PresignResponse presign(long userId, PresignRequest req) {
        ensureBucket();
        String objectKey = buildKey(userId, req == null ? null : req.getFilename());
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(config.getBucket())
                            .object(objectKey)
                            .expiry(config.getPresignExpirySeconds(), TimeUnit.SECONDS)
                            .build());

            PresignResponse resp = new PresignResponse();
            resp.setUploadUrl(uploadUrl);
            resp.setMethod("PUT");
            resp.setObjectKey(objectKey);
            resp.setDownloadUrl(downloadUrl(objectKey));
            resp.setExpiresInSeconds(config.getPresignExpirySeconds());
            return resp;
        } catch (Exception e) {
            throw new IllegalStateException("presign failed: " + e.getMessage(), e);
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(config.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.getBucket()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("ensure bucket failed: " + e.getMessage(), e);
        }
    }

    private static String buildKey(long userId, String filename) {
        String ext = "";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                ext = filename.substring(dot).toLowerCase();
            }
        }
        return userId + "/" + LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private String downloadUrl(String objectKey) {
        String base = config.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = config.getEndpoint();
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + config.getBucket() + "/" + objectKey;
    }
}
