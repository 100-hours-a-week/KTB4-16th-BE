package com.ktb4.team16.mulo.upload.storage;

import com.ktb4.team16.mulo.upload.config.S3Properties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "s3")
public class S3PhotoStorageService implements PhotoStorageService {
    private static final Duration DEFAULT_SIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .build());
    }

    @Override
    public String createReadSignedUrl(String objectKey) {
        return createReadSignedUrl(objectKey, DEFAULT_SIGNED_URL_EXPIRATION);
    }

    @Override
    public String createReadSignedUrl(String objectKey, Duration expiration) {
        if (expiration == null || expiration.isNegative() || expiration.isZero()
                || expiration.getNano() != 0) {
            throw new IllegalArgumentException("Signed URL expiration must be positive seconds");
        }
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .build();
        GetObjectPresignRequest signRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(signRequest).url().toString();
    }
}
