package com.ktb4.team16.mulo.upload.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GcsConfigTest {

    private static final GcsProperties PROPERTIES = new GcsProperties("project", "bucket");
    private static final String TARGET = "target@project.iam.gserviceaccount.com";

    private static GoogleCredentials fakeSource() {
        return GoogleCredentials.create(new AccessToken("token", new Date(System.currentTimeMillis() + 3_600_000)));
    }

    @Test
    void 가장할_계정이_있으면_서명이_가능한_가장_자격_증명을_쓴다() {
        Storage storage = GcsConfig.create(PROPERTIES, TARGET, GcsConfigTest::fakeSource);

        assertThat(storage.getOptions().getCredentials())
                .isInstanceOf(ImpersonatedCredentials.class)
                .isInstanceOf(ServiceAccountSigner.class);
        assertThat(((ImpersonatedCredentials) storage.getOptions().getCredentials()).getAccount())
                .isEqualTo(TARGET);
    }

    @Test
    void 가장할_계정이_비어_있으면_기본_자격_증명을_조회하지_않는다() {
        AtomicInteger lookups = new AtomicInteger();

        Storage storage = GcsConfig.create(PROPERTIES, "  ", () -> {
            lookups.incrementAndGet();
            return fakeSource();
        });

        assertThat(lookups).hasValue(0);
        assertThat(storage.getOptions().getCredentials()).isNotInstanceOf(ImpersonatedCredentials.class);
        assertThat(storage.getOptions().getProjectId()).isEqualTo("project");
    }

    @Test
    void 가장할_계정이_null이어도_기본_동작이다() {
        Storage storage = GcsConfig.create(PROPERTIES, null, GcsConfigTest::fakeSource);

        assertThat(storage.getOptions().getCredentials()).isNotInstanceOf(ImpersonatedCredentials.class);
        assertThat(NoCredentials.class).isNotNull();
    }
}
