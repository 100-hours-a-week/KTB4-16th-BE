package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.report.client.MonthlyReportAiClient;
import com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary;
import com.ktb4.team16.mulo.report.dto.MonthlyTopArtist;
import com.ktb4.team16.mulo.report.dto.MonthlyTopPlace;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MonthlyReportGenerationServiceTest {
    @Mock private RecordRepository recordRepository;
    @Mock private PlaceRepository placeRepository;
    @Mock private UserRepository userRepository;
    @Mock private MonthlyReportRepository monthlyReportRepository;
    @Mock private MonthlyMoodStatRepository moodStatRepository;
    @Mock private MonthlyPhotoSceneStatRepository photoSceneStatRepository;
    @Mock private MonthlyReportAiClient aiClient;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void createsSnapshotForUserWithActiveRecords() {
        var service = new MonthlyReportGenerationService(recordRepository, placeRepository, userRepository,
                monthlyReportRepository, moodStatRepository, photoSceneStatRepository, aiClient, clock);
        var target = YearMonth.of(2026, 8);
        when(recordRepository.findUsersWithActiveRecordsInPeriod(target.atDay(1).atStartOfDay(),
                target.plusMonths(1).atDay(1).atStartOfDay())).thenReturn(List.of(7L));
        when(monthlyReportRepository.existsByUser_UserIdAndReportYearAndReportMonth(7L, (short) 2026, (short) 8))
                .thenReturn(false);
        when(recordRepository.findMonthlyRecordSummary(7L, target.atDay(1).atStartOfDay(),
                target.plusMonths(1).atDay(1).atStartOfDay()))
                .thenReturn(Optional.of(new MonthlyRecordSummary(2L, 15.0)));
        when(recordRepository.findMonthlyTopPlaces(7L, target.atDay(1).atStartOfDay(),
                target.plusMonths(1).atDay(1).atStartOfDay(), PageRequest.of(0, 1)))
                .thenReturn(List.of(new MonthlyTopPlace(3L)));
        when(recordRepository.findMonthlyTopArtists(7L, target.atDay(1).atStartOfDay(),
                target.plusMonths(1).atDay(1).atStartOfDay(), PageRequest.of(0, 1)))
                .thenReturn(List.of(new MonthlyTopArtist("뮤로")));
        when(userRepository.getReferenceById(7L)).thenReturn(User.signup("user@test.com", "hash", "사용자"));
        when(aiClient.generate(2026, 8, "뮤로", false)).thenReturn(new MonthlyReportAiClient.Result("회고", List.of()));
        when(monthlyReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generate(target);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }
}
