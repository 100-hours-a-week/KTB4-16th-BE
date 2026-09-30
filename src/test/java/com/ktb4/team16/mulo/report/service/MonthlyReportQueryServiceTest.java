package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyReportQueryServiceTest {
    @Mock private MonthlyReportRepository reports;
    @Mock private MonthlyMoodStatRepository moods;
    @Mock private MonthlyPhotoSceneStatRepository scenes;

    // 상세 조회가 대표 장소 이름과 통계를 값 DTO로 완성해 컨트롤러에 전달하는지 확인한다.
    @Test
    void returnsDetailValuesWithoutPassingEntities() {
        User user = User.signup("report@example.test", "hash", "사용자");
        MonthlyReport report = MonthlyReport.prepare(user, (short) 2026, (short) 8, 4, "가수");
        report.assignTopPlace(new Place("1111010100", "테스트동",
                new BigDecimal("37.0000000"), new BigDecimal("127.0000000")));
        report.complete("8월 회고");
        when(reports.findByMonthlyReportIdAndUser_UserIdWithTopPlace(3L, 7L))
                .thenReturn(Optional.of(report));
        when(moods.findByMonthlyReport_MonthlyReportId(3L))
                .thenReturn(Optional.of(MonthlyMoodStat.create(report, new BigDecimal("15.0"))));
        when(scenes.findByMonthlyReport_MonthlyReportIdOrderByCountDesc(3L)).thenReturn(List.of());

        var detail = new MonthlyReportQueryService(reports, moods, scenes).getDetail(7L, 3L);

        assertThat(detail.stats().topPlace().legalDongName()).isEqualTo("테스트동");
        assertThat(detail.stats().averageMoodScore()).isEqualByComparingTo("15.0");
        assertThat(detail.aiRecap().status()).isEqualTo("COMPLETED");
    }
}
