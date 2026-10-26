package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;
import com.ktb4.team16.mulo.place.service.PlaceService;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

class AggregateCacheAspectTests {
    private final Clock clock = Clock.systemUTC();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RecordRepository records = mock(RecordRepository.class);
    private final CheckingStore store = new CheckingStore(new TestAggregateCacheStore(clock));
    private final AggregateResultCache cache = new AggregateResultCache(store,
            new AggregateCacheProperties(true, "127.0.0.1", 16380, "aspect-test",
                    Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofMillis(500),
                    Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ofMillis(100)),
            mapper, meters, clock);
    private final AggregateCacheAspect aspect = new AggregateCacheAspect(cache,
            new PopularCacheBoundaryReader(records, clock), mapper, clock, meters);
    private final TestTransactions transactions = new TestTransactions();

    /** 같은 장소 집합은 순서·중복이 달라도 동일한 typed 결과를 재사용한다. */
    @Test
    void popularCachesNormalizedPlaceSetOutsideDbTransaction() {
        AtomicInteger databaseValue = new AtomicInteger(1);
        AtomicInteger loads = new AtomicInteger();
        when(records.findPopularTrackAggregates(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            loads.incrementAndGet();
            return List.of(new PopularTrackAggregateDto(7L, "song", "artist",
                    (long) databaseValue.get(), LocalDateTime.of(2026, 10, 20, 12, 0),
                    "https://example.com/track/7"));
        });
        when(records.countActiveRecordsAtPlaces(any(), any())).thenAnswer(invocation -> (long) databaseValue.get());
        when(records.findOldestActiveCreatedAtAtPlaces(any(), any())).thenReturn(Optional.empty());
        AspectJProxyFactory factory = new AspectJProxyFactory(new PlaceService(records));
        factory.addAspect(aspect);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new MatchAlwaysTransactionAttributeSource());
        factory.addAdvice(interceptor);
        PlaceService service = factory.getProxy();

        PopularTracksResponseDto first = service.getPopularTracks(List.of(2L, 1L, 1L));
        databaseValue.set(2);
        PopularTracksResponseDto second = service.getPopularTracks(List.of(1L, 2L));

        assertThat(first.recordCount()).isEqualTo(1L);
        assertThat(second).isEqualTo(first);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(store.redisDuringTransaction.get()).isFalse();
        assertThat(meters.counter("mulo.aggregate.cache.api.requests", "cache", "popular").count())
                .isEqualTo(2);
    }

    /** 경계 SQL cutoff는 KST 빈이 있어도 원래 서비스의 JVM 기본 시간대를 따른다. */
    @Test
    @ResourceLock("default-time-zone")
    void boundaryCutoffMatchesUtcServiceWithKstClockBean() throws Throwable {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Clock kst = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneId.of("Asia/Seoul"));
            var boundary = new PopularCacheBoundaryReader(records, kst);
            var localAspect = new AggregateCacheAspect(cache, boundary, mapper, kst, meters);
            ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
            when(point.getArgs()).thenReturn(new Object[]{List.of(99L)});
            when(point.proceed()).thenReturn(new PopularTracksResponseDto(0L, List.of()));
            when(records.findOldestActiveCreatedAtAtPlaces(any(), any())).thenReturn(Optional.empty());

            localAspect.popular(point);

            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            org.mockito.Mockito.verify(records).findOldestActiveCreatedAtAtPlaces(any(), cutoff.capture());
            assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 19, 12, 0));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    /** 외부 트랜잭션에 참여한 쓰기는 실제 commit 후에만 이전 결과를 무효화한다. */
    @Test
    void joiningWriteInvalidatesOnlyAfterOuterCommit() throws Throwable {
        AtomicInteger value = new AtomicInteger(1);
        assertThat(cachedNumber(value)).isEqualTo(1);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn("written");

        new TransactionTemplate(transactions).execute(status -> {
            callWrite(point);
            value.set(2);
            assertThat(uncheckedCachedNumber(value)).isEqualTo(1);
            return null;
        });

        assertThat(cachedNumber(value)).isEqualTo(2);
    }

    /** 롤백된 쓰기는 기존 유효 결과를 버려 불필요한 DB 재집계를 만들지 않는다. */
    @Test
    void rollbackPreservesCachedResult() throws Throwable {
        AtomicInteger value = new AtomicInteger(1);
        assertThat(cachedNumber(value)).isEqualTo(1);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn("written");

        new TransactionTemplate(transactions).execute(status -> {
            callWrite(point);
            status.setRollbackOnly();
            return null;
        });

        assertThat(cachedNumber(value)).isEqualTo(1);
        assertThat(meters.counter("mulo.aggregate.cache.requests", "cache", "global",
                "outcome", "invalidate").count()).isZero();
    }

    /** 실패한 원래 쓰기의 예외와 기존 캐시 결과를 그대로 유지한다. */
    @Test
    void failingWriteDoesNotInvalidate() throws Throwable {
        AtomicInteger value = new AtomicInteger(1);
        cachedNumber(value);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenThrow(new IllegalArgumentException("rejected"));

        assertThatThrownBy(() -> aspect.afterSuccessfulWrite(point))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("rejected");
        assertThat(cachedNumber(value)).isEqualTo(1);
        assertThat(meters.counter("mulo.aggregate.cache.requests", "cache", "global",
                "outcome", "invalidate").count()).isZero();
    }

    /** 인기곡 opt-in이 개인 지역 통계 서비스의 결과를 캐싱하지 않는지 검증한다. */
    @Test
    void personalRegionQueryIsNotCachedByPopularFeature() {
        var target = mock(com.ktb4.team16.mulo.record.service.RecordService.class);
        AtomicInteger calls = new AtomicInteger();
        when(target.getMyRecordRegions(1L)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return List.of();
        });
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        com.ktb4.team16.mulo.record.service.RecordService service = factory.getProxy();

        service.getMyRecordRegions(1L);
        service.getMyRecordRegions(1L);

        assertThat(calls).hasValue(2);
        assertThat(meters.find("mulo.aggregate.cache.api.requests").tag("cache", "regions").counter())
                .isNull();
    }

    /** 기존 DB snapshot 안의 인기곡 조회도 Redis와 경계 조회를 추가하지 않는다. */
    @Test
    void popularInsideExistingTransactionBypassCache() throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[]{List.of(1L)});
        when(point.proceed()).thenReturn("snapshot-result");

        new TransactionTemplate(transactions).execute(status -> {
            try {
                assertThat(aspect.popular(point)).isEqualTo("snapshot-result");
            } catch (Throwable failure) {
                throw new AssertionError(failure);
            }
            return null;
        });

        assertThat(store.redisDuringTransaction.get()).isFalse();
        assertThat(meters.counter("mulo.aggregate.cache.requests", "cache", "popular",
                "outcome", "load").count()).isZero();
    }

    /** 실제 캐시 엔진에 현재 DB 값의 간단한 로더를 연결한다. */
    private Object cachedNumber(AtomicInteger value) throws Throwable {
        JavaType type = mapper.getTypeFactory().constructType(Integer.class);
        return cache.get("regions", "user-1", type,
                () -> new AggregateResultCache.Loaded(value.get(), Long.MAX_VALUE));
    }

    /** 트랜잭션 callback 안에서 캐시 조회의 예외를 테스트 실패로 전환한다. */
    private Object uncheckedCachedNumber(AtomicInteger value) {
        try {
            return cachedNumber(value);
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    /** 트랜잭션 callback 안에서 원래 쓰기 advice를 실행한다. */
    private void callWrite(ProceedingJoinPoint point) {
        try {
            aspect.afterSuccessfulWrite(point);
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    /** 실제 Spring 동기화와 commit/rollback callback을 실행하는 테스트 관리자다. */
    private static final class TestTransactions extends AbstractPlatformTransactionManager {
        /** 각 테스트 호출에 별도의 트랜잭션 상태를 제공한다. */
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        /** 실제 DB 없이 Spring의 트랜잭션 동기화 상태만 시작한다. */
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        /** Spring이 정상 commit callback을 이어 실행하도록 한다. */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        /** Spring이 rollback callback을 이어 실행하도록 한다. */
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    /** 캐시 I/O가 DB 트랜잭션 내부로 들어오는지를 실제 호출마다 관측한다. */
    private static final class CheckingStore implements AggregateCacheStore {
        private final AggregateCacheStore delegate;
        private final AtomicBoolean redisDuringTransaction = new AtomicBoolean();

        /** 테스트 Redis 경계의 실제 저장 동작을 위임한다. */
        private CheckingStore(AggregateCacheStore delegate) {
            this.delegate = delegate;
        }

        /** 읽기를 위임하기 전 현재 트랜잭션 상태를 기록한다. */
        @Override
        public String get(String key) {
            observe();
            return delegate.get(key);
        }

        /** 저장을 위임하기 전 현재 트랜잭션 상태를 기록한다. */
        @Override
        public void set(String key, String value, Duration ttl) {
            observe();
            delegate.set(key, value, ttl);
        }

        /** 잠금 획득을 위임하기 전 현재 트랜잭션 상태를 기록한다. */
        @Override
        public boolean putIfAbsent(String key, String value, Duration ttl) {
            observe();
            return delegate.putIfAbsent(key, value, ttl);
        }

        /** 잠금 해제를 위임하기 전 현재 트랜잭션 상태를 기록한다. */
        @Override
        public void releaseLock(String key, String token) {
            observe();
            delegate.releaseLock(key, token);
        }

        /** 실제 DB 트랜잭션 안의 Redis 호출을 누적해 검출한다. */
        private void observe() {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                redisDuringTransaction.set(true);
            }
        }
    }
}
