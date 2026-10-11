package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class MonthlyReportPreparationTransactionTest {
    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 9);

    // 1,001명을 준비하면 최대 500명씩 세 번 별도 커밋되는지 검증한다.
    @Test
    void commitsOneTransactionPerFiveHundredUsers() {
        try (var context = new AnnotationConfigApplicationContext(TransactionTestConfig.class)) {
            var records = context.getBean(RecordRepository.class);
            var reports = context.getBean(MonthlyReportRepository.class);
            var transactions = context.getBean(RecordingTransactionManager.class);
            when(records.findUsersWithActiveRecordsInPeriod(any(), any()))
                    .thenAnswer(invocation -> {
                        transactions.recordActiveUserLookup();
                        return userIds(1_001);
                    });
            when(reports.findByUser_UserIdAndReportYearAndReportMonth(anyLong(), anyShort(), anyShort()))
                    .thenAnswer(invocation -> {
                        transactions.recordUserLookup();
                        return Optional.of(completedReport());
                    });

            context.getBean(MonthlyReportPreparationService.class).prepare(TARGET_MONTH);

            assertThat(transactions.commits).isEqualTo(4);
            assertThat(transactions.rollbacks).isZero();
            assertThat(transactions.committedUserCounts).containsExactly(0, 500, 500, 1);
            assertThat(transactions.committedActiveUserLookupCounts).containsExactly(1, 0, 0, 0);
            assertThat(transactions.readOnlyTransactions).containsExactly(true, false, false, false);
        }
    }

    // 두 번째 묶음이 실패하면 첫 묶음 커밋은 유지되고 실패 묶음만 롤백되는지 검증한다.
    @Test
    void rollsBackOnlyTheFailingChunkAndKeepsEarlierCommit() {
        try (var context = new AnnotationConfigApplicationContext(TransactionTestConfig.class)) {
            var records = context.getBean(RecordRepository.class);
            var reports = context.getBean(MonthlyReportRepository.class);
            var transactions = context.getBean(RecordingTransactionManager.class);
            when(records.findUsersWithActiveRecordsInPeriod(any(), any()))
                    .thenAnswer(invocation -> {
                        transactions.recordActiveUserLookup();
                        return userIds(1_001);
                    });
            when(reports.findByUser_UserIdAndReportYearAndReportMonth(anyLong(), anyShort(), anyShort()))
                    .thenAnswer(invocation -> {
                        transactions.recordUserLookup();
                        long userId = invocation.getArgument(0);
                        if (userId == 501L) {
                            throw new IllegalStateException("second chunk failed");
                        }
                        return Optional.of(completedReport());
                    });

            assertThatThrownBy(() -> context.getBean(MonthlyReportPreparationService.class)
                    .prepare(TARGET_MONTH)).isInstanceOf(IllegalStateException.class);

            assertThat(transactions.commits).isEqualTo(2);
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.committedUserCounts).containsExactly(0, 500);
            assertThat(transactions.committedActiveUserLookupCounts).containsExactly(1, 0);
            assertThat(transactions.rolledBackUserCounts).containsExactly(1);
            assertThat(transactions.rolledBackActiveUserLookupCounts).containsExactly(0);
        }
    }

    // 트랜잭션 경계 테스트에 사용할 순서가 고정된 사용자 ID 목록을 만든다.
    private static java.util.List<Long> userIds(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    // 집계 쿼리를 실행하지 않고 기존 완료 리포트 경로를 통과시킨다.
    private static MonthlyReport completedReport() {
        return MonthlyReport.create(User.signup("complete@example.test", "hash", "완료"),
                (short) 2026, (short) 9, 0, null, "완료");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionTestConfig {
        @Bean
        // 사용자 기간 기록 조회를 테스트가 제어할 수 있도록 목으로 제공한다.
        RecordRepository records() {
            return mock(RecordRepository.class);
        }

        @Bean
        // 대표 장소 조회 경계를 테스트용 목으로 제공한다.
        PlaceRepository places() {
            return mock(PlaceRepository.class);
        }

        @Bean
        // 월간 리포트 사용자 참조 조회를 테스트용 목으로 제공한다.
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        // 리포트 상태 조회 결과와 실패 지점을 테스트가 제어하도록 목으로 제공한다.
        MonthlyReportRepository reports() {
            return mock(MonthlyReportRepository.class);
        }

        @Bean
        // 기분 통계 저장 경계를 테스트용 목으로 제공한다.
        MonthlyMoodStatRepository moods() {
            return mock(MonthlyMoodStatRepository.class);
        }

        @Bean
        // 서비스 프록시가 시작·커밋·롤백한 트랜잭션 수를 기록한다.
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        // 사용자별 집계와 저장을 담당하는 트랜잭션 묶음 서비스를 구성한다.
        MonthlyReportPreparationChunkService chunkService(RecordRepository records,
                PlaceRepository places, UserRepository users, MonthlyReportRepository reports,
                MonthlyMoodStatRepository moods) {
            return new MonthlyReportPreparationChunkService(records, places, users, reports, moods);
        }

        @Bean
        // 활성 사용자를 나누고 묶음 결과를 합치는 준비 오케스트레이터를 구성한다.
        MonthlyReportPreparationService preparationService(MonthlyReportRepository reports,
                MonthlyReportPreparationChunkService chunkService) {
            return new MonthlyReportPreparationService(reports, chunkService);
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;
        private int currentUserLookups;
        private int currentActiveUserLookups;
        private final List<Integer> committedUserCounts = new ArrayList<>();
        private final List<Integer> rolledBackUserCounts = new ArrayList<>();
        private final List<Integer> committedActiveUserLookupCounts = new ArrayList<>();
        private final List<Integer> rolledBackActiveUserLookupCounts = new ArrayList<>();
        private final List<Boolean> readOnlyTransactions = new ArrayList<>();

        // 현재 묶음에서 조회한 사용자 수를 트랜잭션 결과와 연결한다.
        void recordUserLookup() {
            currentUserLookups++;
        }

        // 활성 사용자 ID 조회가 별도 읽기 전용 트랜잭션인지 기록한다.
        void recordActiveUserLookup() {
            currentActiveUserLookups++;
        }

        @Override
        // 실제 DB 대신 테스트에서 새 트랜잭션 경계만 추적한다.
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        // 이 테스트 트랜잭션은 외부 자원을 시작하지 않는다.
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            readOnlyTransactions.add(definition.isReadOnly());
            currentUserLookups = 0;
            currentActiveUserLookups = 0;
        }

        @Override
        // 묶음의 정상 종료 횟수를 기록한다.
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
            committedUserCounts.add(currentUserLookups);
            committedActiveUserLookupCounts.add(currentActiveUserLookups);
            currentUserLookups = 0;
            currentActiveUserLookups = 0;
        }

        @Override
        // 예외로 종료된 묶음 횟수를 기록한다.
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
            rolledBackUserCounts.add(currentUserLookups);
            rolledBackActiveUserLookupCounts.add(currentActiveUserLookups);
            currentUserLookups = 0;
            currentActiveUserLookups = 0;
        }
    }
}
