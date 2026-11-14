package com.clouddocs.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final long expirySeconds;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner presigner,
            @Value("${aws.s3.presign-expiry-seconds:900}") long expirySeconds) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.expirySeconds = expirySeconds;
    }

    public void uploadReplacement(String bucket,String key,java.io.InputStream input,long size){
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/octet-stream").contentLength(size).build(),software.amazon.awssdk.core.sync.RequestBody.fromInputStream(input,size));
    }

    public PresignedResult createUploadUrl(
            String bucket, String key, String contentType, long contentLength) {
        // Sign the reserved size, but keep Content-Type unsigned as in the working upload flow.
        PutObjectRequest put =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentLength(contentLength)
                        .build();

        PutObjectPresignRequest request =
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expirySeconds))
                        .putObjectRequest(put)
                        .build();

        return new PresignedResult(presigner.presignPutObject(request).url(), expirySeconds);
    }

    public PresignedResult createDownloadUrl(String bucket, String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();

        GetObjectPresignRequest request =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expirySeconds))
                        .getObjectRequest(get)
                        .build();

        return new PresignedResult(presigner.presignGetObject(request).url(), expirySeconds);
    }

    public software.amazon.awssdk.services.s3.model.HeadObjectResponse headObject(
            String bucket, String key) {

        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void deleteIfExists(String bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        // S3 cho phép gọi deleteObject với key không tồn tại.
        // Nếu lỗi quyền truy cập hoặc lỗi kết nối xảy ra,
        // để lỗi truyền lên tầng Service xử lý.
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public record PresignedResult(java.net.URL url, long expiresInSeconds) {}
}
