package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary;
import com.ktb4.team16.mulo.report.dto.MonthlyTopArtist;
import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MonthlyReportPreparationServiceTest {
    @Mock private RecordRepository records;
    @Mock private PlaceRepository places;
    @Mock private UserRepository users;
    @Mock private MonthlyReportRepository reports;
    @Mock private MonthlyMoodStatRepository moods;
    @Mock private MonthlyReportPreparationChunkService chunkService;

    // 외부 AI 호출 전에 준비된 사용자 리포트에 동일한 요청 ID를 저장한다.
    @Test
    void reservesBatchRequestIdOnPreparedReport() {
        YearMonth target = YearMonth.of(2026, 9);
        MonthlyReport report = MonthlyReport.prepare(
                User.signup("batch@example.test", "hash", "사용자"),
                (short) 2026, (short) 9, 2, "가수");
        when(reports.findForUpdate(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(report));
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L), 1, 0);

        new MonthlyReportPreparationService(reports, chunkService)
                .reserveBatchRequestId(batch, "4bc555fc-d70b-4060-8485-4e7664e164ab");

        assertThat(report.getBatchRequestId()).isEqualTo("4bc555fc-d70b-4060-8485-4e7664e164ab");
    }

    // 이전 AI 요청의 늦은 실패가 새 시도의 PENDING 상태를 바꾸지 못하게 한다.
    @Test
    void ignoresFailureFromSupersededBatchAttempt() {
        YearMonth target = YearMonth.of(2026, 9);
        MonthlyReport report = MonthlyReport.prepare(
                User.signup("superseded@example.test", "hash", "사용자"),
                (short) 2026, (short) 9, 2, "가수");
        report.assignBatchRequestId("current-batch");
        when(reports.findForUpdate(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(report));
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L), 1, 0);

        new MonthlyReportPreparationService(reports, chunkService)
                .markFailed(batch, "older-batch");

        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.PENDING);
        assertThat(report.getBatchRequestId()).isEqualTo("current-batch");
    }

    // 청크 트랜잭션 예외를 다시 던지면서 월과 청크 위치를 운영 로그에 남긴다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsFailedChunkAndPropagatesItsException(CapturedOutput output) {
        YearMonth target = YearMonth.of(2026, 9);
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(chunkService.findActiveUserIds(target)).thenReturn(List.of(7L, 8L));
        when(chunkService.prepareChunk(target, List.of(7L, 8L))).thenThrow(failure);

        MonthlyReportPreparationService service = new MonthlyReportPreparationService(reports, chunkService);

        assertThatThrownBy(() -> service.prepare(target)).isSameAs(failure);
        assertThat(output.getOut())
                .contains("month=2026-09", "chunkIndex=1", "chunkSize=2");
    }

    @Test
    void preparesPendingSnapshotForActiveUser() {
        YearMonth target = YearMonth.of(2026, 8);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 8))
                .thenReturn(Optional.empty());
        when(records.findMonthlyRecordSummary(7L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(2L, 15.0)));
        when(records.findMonthlyTopPlaces(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(records.findMonthlyTopArtists(7L, start, end, PageRequest.of(0, 1)))
                .thenReturn(List.of(new MonthlyTopArtist("뮤로")));
        when(users.getReferenceById(7L)).thenReturn(User.signup("user@example.test", "hash", "사용자"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var batch = new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(7L));

        assertThat(batch.userIds()).containsExactly(7L);
        assertThat(batch.createdCount()).isEqualTo(1);
    }

    // 소수 둘째 자리 이상의 평균 기분 점수도 화면 저장 단위인 소수 첫째 자리로 반올림한다.
    @Test
    void roundsAverageMoodScoreToOneDecimalPlace() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.empty());
        when(records.findMonthlyRecordSummary(7L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(3L, 3.66)));
        when(records.findMonthlyTopPlaces(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(records.findMonthlyTopArtists(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(users.getReferenceById(7L)).thenReturn(User.signup("user@example.test", "hash", "사용자"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(7L));

        ArgumentCaptor<MonthlyMoodStat> stat = ArgumentCaptor.forClass(MonthlyMoodStat.class);
        verify(moods).save(stat.capture());
        assertThat(stat.getValue().getAverageMoodScore()).isEqualByComparingTo("3.7");
    }

    // 실패한 리포트는 기존 월간 집계를 보존한 채 PENDING으로 되돌려 AI 재요청 대상에 넣는다.
    @Test
    void retriesFailedReportWithoutCreatingDuplicateMonthlyReport() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        MonthlyReport failed = MonthlyReport.prepare(User.signup("user@example.test", "hash", "사용자"),
                (short) 2026, (short) 9, 3, "뮤로");
        failed.markFailedUnlessCompleted();

        when(reports.findByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(failed));

        var batch = new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(7L));

        assertThat(batch.userIds()).containsExactly(7L);
        assertThat(batch.createdCount()).isZero();
        assertThat(batch.skippedCount()).isZero();
        assertThat(failed.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.PENDING);
        verify(reports, never()).save(any());
        verify(moods, never()).save(any());
    }

    // 완료되었거나 AI 응답을 기다리는 리포트는 중복 요청하지 않는다.
    @Test
    void skipsCompletedAndInProgressReports() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        MonthlyReport completed = MonthlyReport.create(User.signup("complete@example.test", "hash", "완료"),
                (short) 2026, (short) 9, 3, "뮤로", "회고");
        MonthlyReport processing = MonthlyReport.prepare(User.signup("process@example.test", "hash", "처리"),
                (short) 2026, (short) 9, 3, "뮤로");
        processing.markProcessing();
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(completed));
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(8L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(processing));
        var batch = new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(7L, 8L));

        assertThat(batch.userIds()).isEmpty();
        assertThat(batch.createdCount()).isZero();
        assertThat(batch.skippedCount()).isEqualTo(2);
        verify(reports, never()).save(any());
        verify(moods, never()).save(any());
    }

    // 앞선 묶음이 커밋된 뒤 준비 작업이 중단되면 저장된 PENDING 사용자를 재시도 대상에 포함한다.
    @Test
    void includesPendingSnapshotWhenPreparationIsRetried() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        MonthlyReport pending = MonthlyReport.prepare(User.signup("pending@example.test", "hash", "대기"),
                (short) 2026, (short) 9, 3, "뮤로");
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(9L, (short) 2026, (short) 9))
                .thenReturn(Optional.of(pending));

        var batch = new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(9L));

        assertThat(batch.userIds()).containsExactly(9L);
        assertThat(batch.createdCount()).isZero();
        assertThat(batch.skippedCount()).isZero();
        verify(reports, never()).save(any());
        verify(moods, never()).save(any());
    }

    // 대상 ID 조회 후 기록이 모두 삭제된 사용자는 빈 집계를 저장하지 않고 건너뛴다.
    @Test
    void skipsUserWhenRecordsDisappearBeforeChunkAggregation() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 9))
                .thenReturn(Optional.empty());
        when(reports.findByUser_UserIdAndReportYearAndReportMonth(8L, (short) 2026, (short) 9))
                .thenReturn(Optional.empty());
        when(records.findMonthlyRecordSummary(7L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(0L, null)));
        when(records.findMonthlyRecordSummary(8L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(2L, 4.2)));
        when(records.findMonthlyTopPlaces(8L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(records.findMonthlyTopArtists(8L, start, end, PageRequest.of(0, 1)))
                .thenReturn(List.of(new MonthlyTopArtist("뮤로")));
        when(users.getReferenceById(8L)).thenReturn(User.signup("active@example.test", "hash", "활성"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var batch = new MonthlyReportPreparationChunkService(records, places, users, reports, moods)
                .prepareChunk(target, List.of(7L, 8L));

        assertThat(batch.userIds()).containsExactly(8L);
        assertThat(batch.createdCount()).isEqualTo(1);
        assertThat(batch.skippedCount()).isEqualTo(1);
        verify(records, never()).findMonthlyTopPlaces(
                eq(7L), eq(start), eq(end), eq(PageRequest.of(0, 1)));
        verify(records, never()).findMonthlyTopArtists(
                eq(7L), eq(start), eq(end), eq(PageRequest.of(0, 1)));
        verify(records).findMonthlyTopPlaces(8L, start, end, PageRequest.of(0, 1));
        verify(records).findMonthlyTopArtists(8L, start, end, PageRequest.of(0, 1));
        ArgumentCaptor<MonthlyReport> report = ArgumentCaptor.forClass(MonthlyReport.class);
        verify(reports).save(report.capture());
        assertThat(report.getValue().getRecordCount()).isEqualTo(2);
        ArgumentCaptor<MonthlyMoodStat> stat = ArgumentCaptor.forClass(MonthlyMoodStat.class);
        verify(moods).save(stat.capture());
        assertThat(stat.getValue().getAverageMoodScore()).isEqualByComparingTo("4.2");
    }
}
