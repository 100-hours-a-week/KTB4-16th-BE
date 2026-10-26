package com.ktb4.team16.mulo.global.cache.aggregate;

import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** 기존 서비스 경계를 보존하며 선택적으로 캐시를 적용하는 인기곡 전용 aspect다. */
@Aspect
@Component
@Profile("popular-cache")
@ConditionalOnProperty(prefix = "mulo.popular-cache", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AggregateCacheAspect {
    private final AggregateResultCache cache;
    private final PopularCacheBoundaryReader boundary;
    private final Clock clock;
    private final MeterRegistry meters;
    private final JavaType popularType;

    /** 캐시·도메인 유효기간·직렬화·서비스 시각·요청 계측 의존성을 주입한다. */
    public AggregateCacheAspect(AggregateResultCache cache, PopularCacheBoundaryReader boundary,
            JsonMapper mapper, Clock clock, MeterRegistry meters) {
        this.cache = cache;
        this.boundary = boundary;
        // 원래 인기곡 서비스의 LocalDateTime.now()와 동일한 JVM 시간대로 경계를 계산한다.
        this.clock = clock.withZone(ZoneId.systemDefault());
        this.meters = meters;
        this.popularType = mapper.getTypeFactory().constructType(PopularTracksResponseDto.class);
    }

    /** 장소 집합별 인기곡을 저장하되 최근 7일에서 빠지는 시각을 넘겨 재사용하지 않는다. */
    @Around("execution(* com.ktb4.team16.mulo.place.service.PlaceService.getPopularTracks(..))")
    public Object popular(ProceedingJoinPoint point) throws Throwable {
        meters.counter("mulo.aggregate.cache.api.requests", "cache", "popular").increment();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 다른 조회가 이미 연 snapshot을 새 캐시 세대에 저장하지 않는다.
            meters.counter("mulo.aggregate.cache.requests", "cache", "popular",
                    "outcome", "transaction_bypass").increment();
            return point.proceed();
        }
        @SuppressWarnings("unchecked")
        List<Long> placeIds = (List<Long>) point.getArgs()[0];
        String requestKey = placeIds.stream().distinct().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
        return cache.get("popular", requestKey, popularType, () -> {
            // 원래 서비스 조회보다 이른 경계로 검사하여 조회 중 경과한 기록도 보수적으로 만료한다.
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(7);
            Object response = point.proceed();
            return new AggregateResultCache.Loaded(response, boundary.validUntil(placeIds, cutoff));
        });
    }

    /** 생성·삭제·탈퇴가 commit된 뒤에만 모든 집계의 캐시 세대를 변경한다. */
    @Around("execution(* com.ktb4.team16.mulo.record.service.RecordService.createRecord(..)) || "
            + "execution(* com.ktb4.team16.mulo.record.service.RecordService.deleteRecord(..)) || "
            + "execution(* com.ktb4.team16.mulo.user.service.UserWithdrawalService.withdraw(..))")
    public Object afterSuccessfulWrite(ProceedingJoinPoint point) throws Throwable {
        Object response = point.proceed();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 외부 트랜잭션에 참여한 쓰기는 실제 commit 이후에만 무효화한다. */
                @Override
                public void afterCommit() {
                    cache.invalidate();
                }
            });
        } else {
            // 이 aspect보다 안쪽에서 실행한 서비스 트랜잭션은 이미 commit을 마쳤다.
            cache.invalidate();
        }
        return response;
    }
}
