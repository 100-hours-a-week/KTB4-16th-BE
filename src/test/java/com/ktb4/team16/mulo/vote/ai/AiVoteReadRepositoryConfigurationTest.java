package com.ktb4.team16.mulo.vote.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AiVoteReadRepositoryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiVoteJdbcConfiguration.class,
                    AdapterBeans.class);

    @Test
    void createsUnavailableAdapterWithoutAiDatabaseSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiVoteReadRepository.class);
            assertThat(context.getBean(AiVoteReadRepository.class).isAvailable()).isFalse();
        });
    }

    @Test
    void rejectsEnabledConfigurationWithoutCredentials() {
        contextRunner.withPropertyValues("mulo.ai-db.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AiVoteJdbcRepository.class, UnavailableAiVoteReadRepository.class})
    static class AdapterBeans { }
}
