package com.ktb4.team16.mulo.upload.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {
    @Bean
    Storage storage(GcsProperties properties) {
        return StorageOptions.newBuilder()
                .setProjectId(properties.projectId())
                .build()
                .getService();
    }
}
