package com.ktb4.team16.mulo.report.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.ktb4.team16.mulo.global.security.ReportOperationTokenFilter;
import com.ktb4.team16.mulo.global.security.SecurityErrorWriter;

/** 운영 토큰이 주입된 배포에서만 수동 리포트 실행 기능을 활성화한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("report.operation.token")
@EnableConfigurationProperties(ReportOperationProperties.class)
public class ReportOperationConfiguration {
    // 운영 토큰 설정과 함께만 내부 수동 실행 토큰 필터를 등록한다.
    @Bean
    ReportOperationTokenFilter reportOperationTokenFilter(ReportOperationProperties properties,
            SecurityErrorWriter errorWriter) {
        return new ReportOperationTokenFilter(properties, errorWriter);
    }
}
