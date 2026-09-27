package com.ktb4.team16.mulo.upload.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.SignUrlOption;
import com.ktb4.team16.mulo.upload.config.GcsProperties;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleCloudStorageService implements GcsStorageService {
    private static final long SIGNED_URL_MINUTES = 10L;

    private final Storage storage;
    private final GcsProperties properties;

    @Override
    public void upload(String objectKey, byte[] content, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(properties.bucketName(), objectKey)
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, content);
    }

    @Override
    public void delete(String objectKey) {
        storage.delete(properties.bucketName(), objectKey);
    }

    @Override
    public String createReadSignedUrl(String objectKey) {
        BlobInfo blobInfo = BlobInfo.newBuilder(properties.bucketName(), objectKey).build();
        URL signedUrl = storage.signUrl(blobInfo, SIGNED_URL_MINUTES, TimeUnit.MINUTES,
                SignUrlOption.withV4Signature());
        return signedUrl.toString();
    }
}
