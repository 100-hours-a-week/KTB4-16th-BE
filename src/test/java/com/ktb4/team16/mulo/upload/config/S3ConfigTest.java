package com.ktb4.team16.mulo.upload.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.upload.storage.GoogleCloudStorageService;
import com.ktb4.team16.mulo.upload.storage.PhotoStorageService;
import com.ktb4.team16.mulo.upload.storage.S3PhotoStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GcsConfig.class, S3Config.class, S3PhotoStorageService.class);

    @Test
    void s3StartsWithoutGcsConfiguration() {
        contextRunner.withPropertyValues(
                "storage.provider=s3",
                "storage.s3.region=ap-northeast-2",
                "storage.s3.bucket-name=photo-bucket")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PhotoStorageService.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    assertThat(context).doesNotHaveBean(Storage.class);
                });
    }

    @Test
    void s3RequiresRegionAndBucket() {
        contextRunner.withPropertyValues("storage.provider=s3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void gcsRemainsDefaultWithoutS3Configuration() {
        new ApplicationContextRunner()
                .withUserConfiguration(GoogleCloudStorageService.class, S3PhotoStorageService.class)
                .withBean(Storage.class, () -> mock(Storage.class))
                .withBean(GcsProperties.class, () -> new GcsProperties("project", "bucket"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PhotoStorageService.class);
                    assertThat(context).hasSingleBean(GoogleCloudStorageService.class);
                    assertThat(context).doesNotHaveBean(S3PhotoStorageService.class);
                });
    }
}
