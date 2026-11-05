package com.ktb4.team16.mulo.report.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import com.ktb4.team16.mulo.report.client.MonthlyReportBatchAiClient;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.AiRecap;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Status;
import com.ktb4.team16.mulo.report.service.MonthlyReportAiCallbackTransactionalService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.ktb4.team16.mulo.report.service.MonthlyReportPreparationChunkService;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 실제 MySQL·Batch 저장소·가짜 AI로 전송 안전성과 재시작 경계를 검증한다. */
@SpringBootTest
class MonthlyReportBatchMysqlTest {
    @Autowired private MonthlyReportJobService jobs;
    @Autowired private MonthlyReportAiCallbackTransactionalService callbacks;
    @Autowired private MonthlyReportDispatchService dispatch;
    @MockitoSpyBean private MonthlyReportBatchStore batchStore;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @MockitoBean private MonthlyReportBatchAiClient ai;
    @MockitoSpyBean private MonthlyReportPreparationChunkService preparation;
    private final java.util.List<Long> extraUsers = new java.util.ArrayList<>();
    private Long userId;
    private Long placeId;
    private Long trackId;
    private YearMonth month;

    /** 각 테스트는 다른 마감 월과 소유 fixture를 사용한다. */
    @BeforeEach
    void seed() {
        reset(ai);
        reset((Object) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(preparation));
        month = YearMonth.of(2026, 1);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM report_batch_runs WHERE target_month=?",
                Integer.class, month.toString()) > 0) {
            month = month.minusMonths(1);
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO users(email,password_hash,nickname) VALUES(?,?,?)",
                id + "@batch.test", "test", id.substring(0, 10));
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class,
                id + "@batch.test");
        jdbc.update("INSERT INTO places(latitude,longitude) VALUES(?,?)", 10 + userId / 1000000.0, 11);
        placeId = jdbc.queryForObject("SELECT place_id FROM places WHERE latitude=? AND longitude=11",
                Long.class, 10 + userId / 1000000.0);
        jdbc.update("INSERT INTO music_tracks(external_track_id,title,artist_name,album_image_url,external_url) "
                + "VALUES(?,?,?,?,?)", id.substring(0,22), "song", "artist", "image", "url");
        trackId = jdbc.queryForObject("SELECT music_track_id FROM music_tracks WHERE external_track_id=?",
                Long.class, id.substring(0,22));
        jdbc.update("INSERT INTO records(user_id,place_id,music_track_id,mood_score,created_at) VALUES(?,?,?,?,?)",
                userId, placeId, trackId, 10, month.atDay(2).atStartOfDay());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new MonthlyReportBatchAiClient.BatchAccepted(null, ((List<?>) call.getArgument(1)).size());
        }).when(ai).requestBatch(any(), any(), anyString());
    }

    /** fixture만 제거하고 Batch 실행 이력은 감사 증거로 둔다. */
    @AfterEach
    void cleanup() {
        for (Long id : extraUsers) {
            jdbc.update("DELETE FROM monthly_mood_stats WHERE monthly_report_id IN "
                    + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?)", id);
            jdbc.update("DELETE FROM monthly_reports WHERE user_id=?", id);
            jdbc.update("DELETE FROM records WHERE user_id=?", id);
            jdbc.update("DELETE FROM users WHERE user_id=?", id);
        }
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id IN "
                + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?) "
                + "AND event_type='MONTHLY_REPORT_CREATED'", userId);
        jdbc.update("DELETE FROM monthly_photo_scene_stats WHERE monthly_report_id IN "
                + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM monthly_mood_stats WHERE monthly_report_id IN "
                + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM monthly_reports WHERE user_id=?", userId);
        jdbc.update("DELETE FROM records WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        jdbc.update("DELETE FROM places WHERE place_id=?", placeId);
        jdbc.update("DELETE FROM music_tracks WHERE music_track_id=?", trackId);
    }

    /** 완료한 월은 다시 보내지 않고 HTTP 중 업무 트랜잭션을 점유하지 않는다. */
    @Test
    void generatesOnceAndPersistsBatchMetadata() {
        var result = jobs.generate(month, false);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(jobs.generate(month, false).createdCount()).isEqualTo(1);
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS='COMPLETED'",
                Integer.class)).isPositive();
        Long executionId = jdbc.queryForObject("SELECT execution_id FROM report_batch_runs WHERE run_key=?",
                Long.class, "GENERATE:" + month);
        assertThat(batchStore.run("GENERATE:" + month).executionId()).isEqualTo(executionId);
        // 비정상 종료 시 업무 마커는 아직 NULL이어도 Batch 메타데이터에서 실행 ID를 찾을 수 있어야 한다.
        jdbc.update("UPDATE report_batch_runs SET execution_id=NULL WHERE run_key=?", "GENERATE:" + month);
        assertThat(batchStore.run("GENERATE:" + month).executionId()).isEqualTo(executionId);
        assertThatThrownBy(() -> batchStore.run("GENERATE:missing"))
                .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_RUN_NOT_FOUND");
        assertThat(state()).isEqualTo("PROCESSING");
    }

    /** timeout 뒤 같은 작업을 재시작해도 새 요청 ID나 AI 호출을 만들지 않는다. */
    @Test
    void unknownIsNotRetransmittedAndLateCallbackStillApplies() {
        doAnswer(call -> { throw new MonthlyReportAiException(); })
                .when(ai).requestBatch(any(), any(), anyString());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        String id = requestId();
        assertThat(state()).isEqualTo("PENDING");
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        assertThatThrownBy(() -> jobs.resume("GENERATE:" + month)).isInstanceOf(ReportBatchException.class)
                .hasMessage("AI_ACCEPTANCE_UNKNOWN");
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
        assertThat(requestId()).isEqualTo(id);
        callbacks.apply(callback(id, Status.COMPLETED));
        callbacks.apply(callback(id, Status.COMPLETED));
        assertThat(state()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id="
                + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?) "
                + "AND event_type='MONTHLY_REPORT_CREATED'", Integer.class, userId)).isEqualTo(1);
    }

    /** AI가 HTTP 응답보다 먼저 콜백해도 접수 저장이 완료 결과와 알림을 되돌리지 않는다. */
    @Test
    void callbackCanCompleteBeforeAcceptanceResponse() {
        doAnswer(call -> {
            callbacks.apply(callback((String) call.getArgument(2), Status.COMPLETED));
            return new MonthlyReportBatchAiClient.BatchAccepted(null, 1);
        }).when(ai).requestBatch(any(), any(), anyString());

        jobs.generate(month, false);

        assertThat(state()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT state FROM report_ai_dispatches WHERE run_key=?",
                String.class, "GENERATE:" + month)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id="
                + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id=?) "
                + "AND event_type='MONTHLY_REPORT_CREATED'", Integer.class, userId)).isEqualTo(1);
    }

    /** 확인된 실패만 별도 재요청하며 원본 기록 삭제 뒤에도 집계를 보존한다. */
    @Test
    void retriesConfirmedFailuresWithoutRecalculatingAndDeduplicatesRegistration() {
        jobs.generate(month, false);
        callbacks.apply(callback(requestId(), Status.FAILED));
        jdbc.update("DELETE FROM records WHERE user_id=?", userId);
        String retryId = UUID.randomUUID().toString();
        jobs.retry(month, retryId);
        jobs.retry(month, retryId);
        verify(ai, times(2)).requestBatch(any(), any(), anyString());
        assertThat(state()).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT record_count FROM monthly_reports WHERE user_id=?",
                Integer.class, userId)).isEqualTo(1);
    }

    /** 콜백 ID가 일치해도 상충 완료 결과로 스냅샷을 덮어쓰지 않는다. */
    @Test
    void rejectsConflictingCompletedCallback() {
        jobs.generate(month, false);
        String id = requestId();
        callbacks.apply(callback(id, Status.COMPLETED));
        var changed = new MonthlyReportAiCallbackRequest(id, null, month.getYear(), month.getMonthValue(),
                OffsetDateTime.now(), List.of(new Result(userId, Status.COMPLETED,
                new AiRecap("different"), List.of(), null)));
        assertThatThrownBy(() -> callbacks.apply(changed)).isInstanceOf(RuntimeException.class);
    }

    /** 두 번째 묶음 저장 뒤 오류를 주입해 첫 500명만 커밋되고 재시작은 나머지만 처리한다. */
    @Test
    void resumesAfterSecondChunkRollbackWithFixedTargets() {
        String prefix = UUID.randomUUID().toString().substring(0, 7);
        for (int i = 0; i < 1199; i++) {
            String email = prefix + i + "@chunk.test";
            jdbc.update("INSERT INTO users(email,password_hash,nickname) VALUES(?,?,?)", email, "test",
                    prefix.substring(0, 5) + i);
            Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
            extraUsers.add(id);
            jdbc.update("INSERT INTO records(user_id,place_id,music_track_id,mood_score,created_at) VALUES(?,?,?,?,?)",
                    id, placeId, trackId, 10, month.atDay(2).atStartOfDay());
        }
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (counter.incrementAndGet() == 2) {
                throw new IllegalStateException("injected second chunk failure after writes");
            }
            return result;
        }).when((MonthlyReportPreparationChunkService) org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(preparation)).prepareForBatch(any(), any());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        assertThat(jdbc.queryForObject("SELECT created_count FROM report_batch_runs WHERE run_key=?",
                Integer.class, "GENERATE:" + month)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_batch_targets WHERE run_key=? AND state='READY'",
                Integer.class, "GENERATE:" + month)).isEqualTo(500);
        reset((Object) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(preparation));
        var result = jobs.generate(month, false);
        assertThat(result.createdCount()).isEqualTo(1200);
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
    }

    /** 동일 재요청 ID의 월 변경은 새 실행으로 허용하지 않는다. */
    @Test
    void rejectsChangedMonthForSameRetryId() {
        String id = UUID.randomUUID().toString();
        jobs.retry(month, id);
        assertThatThrownBy(() -> jobs.retry(month.minusMonths(1), id))
                .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_REQUEST_CONFLICT");
    }

    /** 첫 요청의 AI 대기 중 같은 월의 두 번째 서버 요청은 월별 소유권 충돌로 끝난다. */
    @Test
    void concurrentStartsForSameMonthAreSerialized() throws Exception {
        var requestEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseRequest = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        doAnswer(call -> {
            requestEntered.countDown();
            if (!releaseRequest.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release the AI request");
            }
            return new MonthlyReportBatchAiClient.BatchAccepted(null, ((List<?>) call.getArgument(1)).size());
        }).when(ai).requestBatch(any(), any(), anyString());

        try {
            var first = executor.submit(() -> jobs.generate(month, false));
            try {
                assertThat(requestEntered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> jobs.generate(month, false))
                        .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_ALREADY_RUNNING");
            } finally {
                releaseRequest.countDown();
            }
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).createdCount()).isEqualTo(1);
            verify(ai, times(1)).requestBatch(any(), any(), anyString());
        } finally {
            releaseRequest.countDown();
            executor.shutdownNow();
        }
    }

    /** 접수 확인은 재전송하지 않고 정상 콜백 대기 상태만 복원한다. */
    @Test
    void acceptedReconciliationDoesNotResend() {
        doAnswer(call -> { throw new MonthlyReportAiException(); }).when(ai).requestBatch(any(), any(), anyString());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        String id = requestId();
        jdbc.update("UPDATE report_ai_dispatches SET state='ACCEPTED' WHERE batch_request_id=?", id);
        jobs.resume("GENERATE:" + month);
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
    }

    /** 잘못된 batchRequestId 확인은 서버 오류 대신 명시적인 미존재로 처리한다. */
    @Test
    void missingDispatchReconciliationReturnsNotFound() {
        assertThatThrownBy(() -> dispatch.reconcile(UUID.randomUUID().toString(), false,
                "operator", "not found"))
                .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_DISPATCH_NOT_FOUND");
        assertThatThrownBy(() -> jobs.recoverStopped("GENERATE:missing", 1L,
                true, "operator", "not found"))
                .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_RUN_NOT_FOUND");
    }

    /** 일지 없는 기존 PENDING은 신규 Job에서 회수하지 않고 건너뜀 건수로 남긴다. */
    @Test
    void leavesLegacyPendingForOperatorReview() {
        jdbc.update("INSERT INTO monthly_reports(user_id,report_year,report_month,record_count,ai_recap_status) "
                + "VALUES(?,?,?,1,'PENDING')", userId, month.getYear(), month.getMonthValue());
        var result = jobs.generate(month, false);
        assertThat(result.skippedCount()).isEqualTo(1);
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    /** 전송 시작 직후 종료를 운영 확인 없이 다시 실행해도 최초 전송을 추측하지 않는다. */
    @Test
    void staleSendingIsNeverAutomaticallySent() {
        doAnswer(call -> { throw new MonthlyReportAiException(); }).when(ai).requestBatch(any(), any(), anyString());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        jdbc.update("UPDATE report_ai_dispatches SET state='SENDING' WHERE batch_request_id=?", requestId());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
        assertThat(jdbc.queryForObject("SELECT EXIT_CODE FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 1",
                String.class)).isEqualTo("AI_ACCEPTANCE_UNKNOWN");
    }

    /** 운영자가 미접수 증거를 확인한 경우에만 같은 ID로 명시적 재시작을 허용한다. */
    @Test
    void confirmedNonAcceptanceCanBeExplicitlyRestartedWithSameId() {
        doAnswer(call -> { throw new MonthlyReportAiException(); }).when(ai).requestBatch(any(), any(), anyString());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        String id = requestId();
        dispatch.reconcile(id, false, "operator", "AI owner confirmed not accepted");
        reset(ai);
        doAnswer(call -> new MonthlyReportBatchAiClient.BatchAccepted(null, 1))
                .when(ai).requestBatch(any(), any(), anyString());
        jobs.resume("GENERATE:" + month);
        assertThat(requestId()).isEqualTo(id);
        verify(ai).requestBatch(any(), any(), org.mockito.ArgumentMatchers.eq(id));
    }

    /** 죽은 실행 복구에는 운영자의 종료 확인이 필요하고 대상 실행과 키도 일치해야 한다. */
    @Test
    void recoveryRequiresStoppedConfirmationAndAuditsEvidence() {
        doAnswer(call -> { throw new MonthlyReportAiException(); }).when(ai).requestBatch(any(), any(), anyString());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        Long execution = jdbc.queryForObject("SELECT execution_id FROM report_batch_runs WHERE run_key=?",
                Long.class, "GENERATE:" + month);
        jdbc.update("UPDATE BATCH_JOB_EXECUTION SET STATUS='STARTED',END_TIME=NULL WHERE JOB_EXECUTION_ID=?",
                execution);
        assertThat(jdbc.update("UPDATE BATCH_STEP_EXECUTION SET STATUS='STARTED',END_TIME=NULL "
                + "WHERE JOB_EXECUTION_ID=? AND STEP_NAME LIKE '%send'", execution)).isEqualTo(1);
        assertThatThrownBy(() -> jobs.recoverStopped("GENERATE:" + month, execution, false, "operator", "evidence"))
                .isInstanceOf(ReportBatchException.class);
        jobs.recoverStopped("GENERATE:" + month, execution, true, "operator", "process stopped");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_batch_recoveries WHERE execution_id=?",
                Integer.class, execution)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                String.class, execution)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT STATUS FROM BATCH_STEP_EXECUTION "
                + "WHERE JOB_EXECUTION_ID=? AND STEP_NAME LIKE '%send'", String.class, execution)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT owner_run_key IS NULL FROM report_batch_months WHERE target_month=?",
                Boolean.class, month.toString())).isTrue();
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class)
                .hasMessage("AI_ACCEPTANCE_UNKNOWN");
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
    }

    /** Batch 실행 ID 생성 전에 남은 월 예약도 운영 근거를 남기고 복구할 수 있다. */
    @Test
    void recoversReservationWithoutBatchExecutionId() {
        String key = "GENERATE:" + month;
        Boolean acquired = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> batchStore.acquire(key, month, "GENERATE"));
        assertThat(acquired).isTrue();

        assertThatThrownBy(() -> jobs.recoverStopped(key, null, false, "operator", "process stopped"))
                .isInstanceOf(ReportBatchException.class).hasMessage("REPORT_INVALID_REQUEST");
        jobs.recoverStopped(key, null, true, "operator", "process stopped before Batch metadata");

        assertThat(batchStore.run(key).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_batch_recoveries "
                + "WHERE run_key=? AND execution_id IS NULL", Integer.class, key)).isEqualTo(1);
        assertThat(jobs.generate(month, false).createdCount()).isEqualTo(1);
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
    }

    /** 재시작 중 상태 조회는 이전 실패 실행이 아닌 현재 실행 ID를 노출한다. */
    @Test
    void resumedRunExposesLatestExecutionIdDuringDispatch() {
        var target = (MonthlyReportPreparationChunkService) org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(preparation);
        org.mockito.Mockito.doThrow(new IllegalStateException("first attempt fails"))
                .when(target).prepareForBatch(any(), any());
        assertThatThrownBy(() -> jobs.generate(month, false)).isInstanceOf(ReportBatchException.class);
        Long previous = batchStore.run("GENERATE:" + month).executionId();
        reset(target);
        var exposed = new java.util.concurrent.atomic.AtomicReference<Long>();
        var latest = new java.util.concurrent.atomic.AtomicReference<Long>();
        doAnswer(call -> {
            exposed.set(batchStore.run("GENERATE:" + month).executionId());
            latest.set(jdbc.queryForObject("SELECT MAX(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION", Long.class));
            return new MonthlyReportBatchAiClient.BatchAccepted(null, 1);
        }).when(ai).requestBatch(any(), any(), anyString());
        jobs.generate(month, false);
        assertThat(exposed.get()).isEqualTo(latest.get()).isNotEqualTo(previous);
    }

    /** 고정한 재요청 대상에 늦은 성공이 도착하면 재생성 대신 건너뛰고 완료한다. */
    @Test
    void skipsLateCompletionBeforeRetryReservation() {
        jobs.generate(month, false);
        String originalId = requestId();
        callbacks.apply(callback(originalId, Status.FAILED));
        doAnswer(call -> {
            callbacks.apply(callback(originalId, Status.COMPLETED));
            entityManager.flush();
            return call.callRealMethod();
        }).when(batchStore).reserve(org.mockito.ArgumentMatchers.startsWith("RETRY:"));
        String retryId = UUID.randomUUID().toString();
        var result = jobs.retry(month, retryId);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(state()).isEqualTo("COMPLETED");
        assertThat(batchStore.dispatch("RETRY:" + retryId)).isNull();
        verify(ai, times(1)).requestBatch(any(), any(), anyString());
    }

    /** fixture 리포트의 저장된 AI 상태를 읽는다. */
    private String state() {
        return jdbc.queryForObject("SELECT ai_recap_status FROM monthly_reports WHERE user_id=?", String.class, userId);
    }

    /** 서버가 예약한 전송 ID를 읽는다. */
    private String requestId() {
        return jdbc.queryForObject("SELECT batch_request_id FROM monthly_reports WHERE user_id=?", String.class, userId);
    }

    /** 완료·실패 콜백은 동일 요청 ID·사용자에 연결한다. */
    private MonthlyReportAiCallbackRequest callback(String id, Status status) {
        return new MonthlyReportAiCallbackRequest(id, null, month.getYear(), month.getMonthValue(),
                OffsetDateTime.now(), List.of(new Result(userId, status,
                status == Status.COMPLETED ? new AiRecap("recap") : null,
                status == Status.COMPLETED ? List.of() : null, status == Status.FAILED ? "AI_FAILED" : null)));
    }
}
