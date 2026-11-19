package com.ktb4.team16.mulo.upload.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/cloud-platform");
    private static final int IMPERSONATION_LIFETIME_SECONDS = 3600;

    /**
     * {@code gcs.impersonate-service-account}가 비어 있으면 기본 자격 증명(GCP VM의 서비스 계정 등)을 그대로 쓴다.
     * 값이 있으면 기본 자격 증명으로 그 서비스 계정을 가장한다. GCP 밖(예: AWS)에서 Workload Identity Federation의
     * 외부 계정 자격 증명을 쓸 때 필요하다. 외부 계정 자격 증명 자체는 서명을 할 수 없어서
     * {@code Storage.signUrl}이 실패하지만, 가장한 자격 증명은 IAM {@code signBlob}으로 서명한다.
     */
    @Bean
    Storage storage(
            GcsProperties properties,
            @Value("${gcs.impersonate-service-account:}") String impersonateServiceAccount) {
        return create(properties, impersonateServiceAccount, GcsConfig::applicationDefaultCredentials);
    }

    static Storage create(
            GcsProperties properties,
            String impersonateServiceAccount,
            Supplier<GoogleCredentials> sourceCredentials) {
        StorageOptions.Builder builder = StorageOptions.newBuilder().setProjectId(properties.projectId());
        if (impersonateServiceAccount != null && !impersonateServiceAccount.isBlank()) {
            builder.setCredentials(ImpersonatedCredentials.create(
                    sourceCredentials.get().createScoped(SCOPES),
                    impersonateServiceAccount.trim(),
                    null,
                    SCOPES,
                    IMPERSONATION_LIFETIME_SECONDS));
        }
        return builder.build().getService();
    }

    private static GoogleCredentials applicationDefaultCredentials() {
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException exception) {
            throw new IllegalStateException("기본 자격 증명을 찾을 수 없다", exception);
        }
    }
}
