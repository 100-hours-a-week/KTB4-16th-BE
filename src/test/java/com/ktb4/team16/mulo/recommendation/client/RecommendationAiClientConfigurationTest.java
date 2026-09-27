package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RecommendationAiClientConfigurationTest {
    // Mock 모드에서는 고정 응답 Client 하나만 추천 경계 Bean으로 등록한다.
    @Test
    void registersOnlyMockClientWhenMockModeIsEnabled() {
        contextRunner("ai.mock-enabled=true").run(context -> {
            assertThat(context.getBeansOfType(RecommendationAiClient.class))
                    .hasSize(1).values().singleElement()
                    .isInstanceOf(MockRecommendationAiClient.class);
        });
    }

    // 실제 모드에서는 HTTP Client 하나만 추천 경계 Bean으로 등록한다.
    @Test
    void registersOnlyContextClientWhenMockModeIsDisabled() {
        contextRunner("ai.mock-enabled=false").run(context -> {
            assertThat(context.getBeansOfType(RecommendationAiClient.class))
                    .hasSize(1).values().singleElement()
                    .isInstanceOf(ContextRecommendationAiClient.class);
        });
    }

    // 환경별 AI 속성을 포함한 최소 Spring 컨텍스트를 생성한다.
    private ApplicationContextRunner contextRunner(String mockMode) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of())
                .withUserConfiguration(AiClientConfiguration.class)
                .withPropertyValues(
                        "ai.base-url=https://mulostudio.com/ai",
                        "ai.internal-token=test-token",
                        "ai.connect-timeout=PT1S",
                        "ai.read-timeout=PT1S",
                        mockMode);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    @Import({MockRecommendationAiClient.class, ContextRecommendationAiClient.class})
    static class AiClientConfiguration {
    }
}
