package com.ktb4.team16.mulo.upload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcs")
public record GcsProperties(String projectId, String bucketName) {
}
