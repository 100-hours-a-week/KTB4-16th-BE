package com.ktb4.team16.mulo.music.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpotifyConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SpotifyConfiguration.class)
            .withPropertyValues(
                    "spotify.client-id=client-id",
                    "spotify.client-secret=client-secret",
                    "spotify.accounts-base-url=https://accounts.test",
                    "spotify.api-base-url=https://api.test",
                    "spotify.connect-timeout=PT2S",
                    "spotify.read-timeout=PT3S",
                    "spotify.user-limit-30-seconds=8",
                    "spotify.user-limit-60-seconds=15",
                    "spotify.global-limit-30-seconds=80");

    @Test
    void bindsSpotifyRecordThroughConfigurationPropertiesInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpotifyProperties.class);
            assertThat(context.getBean(SpotifyProperties.class).clientId())
                    .isEqualTo("client-id");
        });
    }
}
