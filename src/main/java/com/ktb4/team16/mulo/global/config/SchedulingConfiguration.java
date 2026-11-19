package com.ktb4.team16.mulo.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 실행할지 속성 하나로 정한다.
 *
 * <p>기본값은 켜짐이라 기존 동작은 그대로다. 같은 DB를 보는 Backend가 두 환경에서 동시에 떠 있는 동안
 * (예: 클라우드 사이 무중단 이전) 한쪽의 예약 작업이 중복으로 돌지 않게 하려면
 * {@code MULO_SCHEDULING_ENABLED=false}로 그쪽 인스턴스의 예약 작업을 끈다.
 *
 * <p>끄면 {@code @EnableScheduling}이 등록되지 않아 알림 아웃박스, 투표, 매칭 만료, 월간 리포트 등
 * {@code @Scheduled}가 붙은 모든 작업이 실행되지 않는다. 빈 자체는 그대로 만들어지므로 다른 곳에서 주입해 써도 된다.
 * 오늘의 노래 작업은 별도 속성({@code mulo.daily-song.scheduler.enabled})이 계속 적용된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "mulo.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
