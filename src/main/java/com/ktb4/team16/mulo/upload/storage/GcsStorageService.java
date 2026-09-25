package com.ktb4.team16.mulo.upload.storage;

public interface GcsStorageService {
    void upload(String objectKey, byte[] content, String contentType);

    void delete(String objectKey);

    String createReadSignedUrl(String objectKey);
}
