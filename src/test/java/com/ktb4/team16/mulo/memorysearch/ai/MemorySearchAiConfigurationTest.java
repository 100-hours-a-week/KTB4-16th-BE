package com.ktb4.team16.mulo.memorysearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestClient;

class MemorySearchAiConfigurationTest {

    /** 어떤 실행 프로필에서도 임시 결과 대신 실제 HTTP 게이트웨이 한 개만 선택한다. */
    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "prod"})
    void selectsRealGatewayInEveryProfile(String profile) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withBean(AiProperties.class, () -> properties(URI.create("http://ai.test")))
                .withUserConfiguration(GatewayScan.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MemorySearchAiGateway.class);
                    assertThat(context.getBean(MemorySearchAiGateway.class))
                            .isInstanceOf(HttpMemorySearchAiGateway.class);
                    assertThat(context.getBean("memorySearchHttpClient", HttpClient.class)
                            .connectTimeout()).contains(Duration.ofSeconds(5));
                });
    }

    /** 실제 연결에서 헤더 뒤 응답 본문이 멈춰도 읽기 제한으로 502를 반환한다. */
    @Test
    void appliesReadTimeoutToAnActualSlowResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/api/memory-search", exchange -> {
            requestArrived.countDown();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        AiProperties properties = new AiProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-token",
                Duration.ofSeconds(1), Duration.ofMillis(250), false);
        MemorySearchAiConfiguration configuration = new MemorySearchAiConfiguration();
        try (HttpClient httpClient = configuration.memorySearchHttpClient(properties)) {
            RestClient client = configuration.memorySearchRestClient(
                    httpClient, properties);
            HttpMemorySearchAiGateway gateway = new HttpMemorySearchAiGateway(client, properties);
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                        assertThatThrownBy(() -> gateway.search(35L, "가을", List.of()))
                                .isInstanceOfSatisfying(MemorySearchDomainException.class,
                                        failure -> assertThat(failure.errorCode())
                                                .isEqualTo(ErrorCode.AI_SERVICE_ERROR)));
                assertThat(requestArrived.await(1, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseResponse.countDown();
            }
        } finally {
            server.stop(0);
        }
    }

    /** 전용 HTTP 설정에 전달할 기존 AI 설정 값을 만든다. */
    private AiProperties properties(URI baseUrl) {
        return new AiProperties(baseUrl, "test-token", Duration.ofSeconds(5), Duration.ofSeconds(30), false);
    }

    /** 실제 패키지 스캔으로 프로필별 다중 빈 또는 임시 게이트웨이 잔존을 확인한다. */
    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = MemorySearchAiGateway.class)
    static class GatewayScan { }
}
