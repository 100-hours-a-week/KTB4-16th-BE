package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportPreparationService {
    private final RecordRepository records;
    private final PlaceRepository places;
    private final UserRepository users;
    private final MonthlyReportRepository reports;
    private final MonthlyMoodStatRepository moods;

    public MonthlyReportPreparationService(RecordRepository records, PlaceRepository places,
            UserRepository users, MonthlyReportRepository reports, MonthlyMoodStatRepository moods) {
        this.records = records;
        this.places = places;
        this.users = users;
        this.reports = reports;
        this.moods = moods;
    }

    // AI 요청 전에 사용자별 월간 집계 스냅샷을 PENDING 상태로 저장한다.
    @Transactional
    public PreparedBatch prepare(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        short year = (short) month.getYear();
        short monthValue = (short) month.getMonthValue();
        List<Long> userIds = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        for (Long userId : records.findUsersWithActiveRecordsInPeriod(start, end)) {
            var existing = reports.findByUser_UserIdAndReportYearAndReportMonth(userId, year, monthValue);
            if (existing.isPresent()) {
                MonthlyReport report = existing.get();
                if (report.getAiRecapStatus() == MonthlyReport.AiRecapStatus.FAILED) {
                    // 기존 월간 집계를 보존하고 실패한 AI 회고만 다시 요청한다.
                    report.retryFailedRecap();
                    userIds.add(userId);
                    continue;
                }
                skipped++;
                continue;
            }
            var summary = records.findMonthlyRecordSummary(userId, start, end).orElseThrow();
            var topPlace = records.findMonthlyTopPlaces(userId, start, end, PageRequest.of(0, 1));
            var topArtist = records.findMonthlyTopArtists(userId, start, end, PageRequest.of(0, 1));
            String artist = topArtist.isEmpty() ? null : topArtist.getFirst().artistName();
            MonthlyReport report = MonthlyReport.prepare(users.getReferenceById(userId), year,
                    monthValue, summary.recordCount().intValue(), artist);
            if (!topPlace.isEmpty()) {
                report.assignTopPlace(places.getReferenceById(topPlace.getFirst().placeId()));
            }
            MonthlyReport saved = reports.save(report);
            // DB의 소수 첫째 자리 평균에 맞춰 중간값 이상은 올림으로 반올림한다.
            moods.save(MonthlyMoodStat.create(saved,
                    BigDecimal.valueOf(summary.averageMoodScore()).setScale(1, RoundingMode.HALF_UP)));
            userIds.add(userId);
            created++;
        }
        return new PreparedBatch(month, List.copyOf(userIds), created, skipped);
    }

    // AI가 배치 생성을 접수한 뒤 준비된 리포트만 PROCESSING으로 전환한다.
    @Transactional
    public void markProcessing(PreparedBatch batch) {
        changeStatus(batch, MonthlyReport::markProcessing);
    }

    // AI 요청이 실패하면 준비된 리포트의 집계 스냅샷은 남기고 FAILED로 전환한다.
    @Transactional
    public void markFailed(PreparedBatch batch) {
        changeStatus(batch, MonthlyReport::markFailedUnlessCompleted);
    }

    // 배치의 사용자별 리포트를 조회해 상태 전이 책임을 엔티티에 위임한다.
    private void changeStatus(PreparedBatch batch, java.util.function.Consumer<MonthlyReport> change) {
        short year = (short) batch.targetMonth().getYear();
        short month = (short) batch.targetMonth().getMonthValue();
        for (Long userId : batch.userIds()) {
            reports.findByUser_UserIdAndReportYearAndReportMonth(userId, year, month)
                    .ifPresent(change);
        }
    }

    public record PreparedBatch(YearMonth targetMonth, List<Long> userIds, int createdCount,
            int skippedCount) { }
}
