package com.ktb4.team16.mulo.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.s3")
public record S3Properties(String region, String bucketName) {
    public S3Properties {
        if (region == null || region.isBlank() || bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("S3 region and bucket name must be configured");
        }
    }
}
