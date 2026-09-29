package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary;
import com.ktb4.team16.mulo.report.dto.MonthlyTopArtist;
import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MonthlyReportPreparationServiceTest {
    @Mock private RecordRepository records;
    @Mock private PlaceRepository places;
    @Mock private UserRepository users;
    @Mock private MonthlyReportRepository reports;
    @Mock private MonthlyMoodStatRepository moods;

    @Test
    void preparesPendingSnapshotForActiveUser() {
        YearMonth target = YearMonth.of(2026, 8);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        when(records.findUsersWithActiveRecordsInPeriod(start, end)).thenReturn(List.of(7L));
        when(reports.existsByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 8))
                .thenReturn(false);
        when(records.findMonthlyRecordSummary(7L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(2L, 15.0)));
        when(records.findMonthlyTopPlaces(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(records.findMonthlyTopArtists(7L, start, end, PageRequest.of(0, 1)))
                .thenReturn(List.of(new MonthlyTopArtist("뮤로")));
        when(users.getReferenceById(7L)).thenReturn(User.signup("user@test.com", "hash", "사용자"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var batch = new MonthlyReportPreparationService(records, places, users, reports, moods)
                .prepare(target);

        assertThat(batch.userIds()).containsExactly(7L);
        assertThat(batch.createdCount()).isEqualTo(1);
    }

    // 소수 둘째 자리 이상의 평균 기분 점수도 화면 저장 단위인 소수 첫째 자리로 반올림한다.
    @Test
    void roundsAverageMoodScoreToOneDecimalPlace() {
        YearMonth target = YearMonth.of(2026, 9);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();
        when(records.findUsersWithActiveRecordsInPeriod(start, end)).thenReturn(List.of(7L));
        when(reports.existsByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 9))
                .thenReturn(false);
        when(records.findMonthlyRecordSummary(7L, start, end))
                .thenReturn(Optional.of(new MonthlyRecordSummary(3L, 3.66)));
        when(records.findMonthlyTopPlaces(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(records.findMonthlyTopArtists(7L, start, end, PageRequest.of(0, 1))).thenReturn(List.of());
        when(users.getReferenceById(7L)).thenReturn(User.signup("user@test.com", "hash", "사용자"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new MonthlyReportPreparationService(records, places, users, reports, moods).prepare(target);

        ArgumentCaptor<MonthlyMoodStat> stat = ArgumentCaptor.forClass(MonthlyMoodStat.class);
        verify(moods).save(stat.capture());
        assertThat(stat.getValue().getAverageMoodScore()).isEqualByComparingTo("3.7");
    }
}
