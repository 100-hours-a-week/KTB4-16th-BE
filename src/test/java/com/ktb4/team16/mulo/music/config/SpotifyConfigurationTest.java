package com.ktb4.team16.mulo.music.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

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
                    "spotify.global-limit-30-seconds=80",
                    "mulo.music-search-rate-limit.namespace=mulo:music-search-rate-limit:test");

    @Test
    void bindsSpotifyRecordThroughConfigurationPropertiesInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpotifyProperties.class);
            assertThat(context.getBean(SpotifyProperties.class).clientId())
                    .isEqualTo("client-id");
        });
    }

    @Test
    void usesLocalNamespaceWithoutEnvironmentOverride() throws IOException {
        assertThat(namespaceWithoutEnvironmentOverride("application-local.yaml"))
                .isEqualTo("mulo:music-search-rate-limit:local");
    }

    @Test
    void usesTestNamespaceWithoutEnvironmentOverride() throws IOException {
        assertThat(namespaceWithoutEnvironmentOverride("application-test.yaml"))
                .isEqualTo("mulo:music-search-rate-limit:test");
    }

    private String namespaceWithoutEnvironmentOverride(String resourceName) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader().load(resourceName, new ClassPathResource(resourceName))
                .forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources)
                .getRequiredProperty("mulo.music-search-rate-limit.namespace");
    }
}
