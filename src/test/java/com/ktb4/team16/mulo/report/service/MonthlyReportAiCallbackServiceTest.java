package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.AiRecap;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.PhotoScene;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Status;
import com.ktb4.team16.mulo.report.entity.MonthlyPhotoSceneStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyReportAiCallbackServiceTest {
    @Mock private MonthlyReportRepository reports;
    @Mock private MonthlyPhotoSceneStatRepository scenes;
    @Mock private User user;

    // 완료 콜백의 회고·장면 통계를 저장하고 선저장된 기록 수를 보존한다.
    @Test
    void completesReportWithRecapAndScenes() {
        MonthlyReport report = report();
        when(reports.findByReportYearAndReportMonth((short) 2026, (short) 8))
                .thenReturn(List.of(report));
        var callback = callback(new Result(7L, Status.COMPLETED,
                new AiRecap("8월 회고"), List.of(new PhotoScene("산책", 2, 100)), null));

        new MonthlyReportAiCallbackService(reports, scenes).apply(callback);

        ArgumentCaptor<MonthlyPhotoSceneStat> saved = ArgumentCaptor.forClass(MonthlyPhotoSceneStat.class);
        verify(scenes).save(saved.capture());
        assertThat(saved.getValue().getSceneTag()).isEqualTo("산책");
        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.COMPLETED);
        assertThat(report.getAiRecapText()).isEqualTo("8월 회고");
        assertThat(report.getRecordCount()).isEqualTo(4);
    }

    // 실패 콜백은 미완료 리포트만 FAILED로 바꾸고 기존 통계는 유지한다.
    @Test
    void failedCallbackKeepsPreparedStats() {
        MonthlyReport report = report();
        report.markProcessing();
        when(reports.findByReportYearAndReportMonth((short) 2026, (short) 8))
                .thenReturn(List.of(report));

        new MonthlyReportAiCallbackService(reports, scenes).apply(
                callback(new Result(7L, Status.FAILED, null, null, "UPSTREAM_UNAVAILABLE")));

        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.FAILED);
        assertThat(report.getRecordCount()).isEqualTo(4);
        assertThat(report.getTopArtistName()).isEqualTo("가수");
    }

    // 재전송된 실패 콜백은 이미 완료된 회고를 되돌리지 않는다.
    @Test
    void lateFailedCallbackDoesNotDowngradeCompletedReport() {
        MonthlyReport report = report();
        report.complete("완료 회고");
        when(reports.findByReportYearAndReportMonth((short) 2026, (short) 8))
                .thenReturn(List.of(report));

        new MonthlyReportAiCallbackService(reports, scenes).apply(
                callback(new Result(7L, Status.FAILED, null, null, "UPSTREAM_UNAVAILABLE")));

        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.COMPLETED);
        assertThat(report.getAiRecapText()).isEqualTo("완료 회고");
    }

    // 테스트용 기존 사용자·월의 선저장 스냅샷을 만든다.
    private MonthlyReport report() {
        when(user.getUserId()).thenReturn(7L);
        return MonthlyReport.prepare(user, (short) 2026, (short) 8, 4, "가수");
    }

    // 콜백의 공통 식별값을 고정하고 사용자별 결과만 바꿔 전달한다.
    private MonthlyReportAiCallbackRequest callback(Result result) {
        return new MonthlyReportAiCallbackRequest("job-1", 2026, 8,
                OffsetDateTime.parse("2026-09-01T03:00:00+09:00"), List.of(result));
    }
}
