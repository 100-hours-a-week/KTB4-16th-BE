package com.ktb4.team16.mulo.memorysearch.ai;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 기억 검색 HTTP 연결을 요청마다 만들지 않고 공유하며 종료 시 자원을 정리한다. */
@Configuration(proxyBeanMethods = false)
public class MemorySearchAiConfiguration {

    /** AI 연결 제한을 적용한 클라이언트를 공유하고 애플리케이션 종료 시 닫는다. */
    @Bean(destroyMethod = "close")
    HttpClient memorySearchHttpClient(AiProperties properties) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    /** 기존 AI 설정의 읽기 제한을 기억 검색 요청에도 적용한다. */
    @Bean
    RestClient memorySearchRestClient(
            @Qualifier("memorySearchHttpClient") HttpClient httpClient, AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
