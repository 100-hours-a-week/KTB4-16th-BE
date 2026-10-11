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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportPreparationChunkService {
    private final RecordRepository records;
    private final PlaceRepository places;
    private final UserRepository users;
    private final MonthlyReportRepository reports;
    private final MonthlyMoodStatRepository moods;

    public MonthlyReportPreparationChunkService(RecordRepository records, PlaceRepository places,
            UserRepository users, MonthlyReportRepository reports, MonthlyMoodStatRepository moods) {
        this.records = records;
        this.places = places;
        this.users = users;
        this.reports = reports;
        this.moods = moods;
    }

    // 집계 대상 사용자 ID를 조회하고 짧은 읽기 전용 트랜잭션을 끝낸다.
    @Transactional(readOnly = true)
    public List<Long> findActiveUserIds(YearMonth targetMonth) {
        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        return records.findUsersWithActiveRecordsInPeriod(start, end);
    }

    // 지정 사용자 묶음의 월간 집계와 리포트·기분 통계를 독립 트랜잭션으로 저장한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedChunk prepareChunk(YearMonth targetMonth, List<Long> userIds) {
        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        short year = (short) targetMonth.getYear();
        short month = (short) targetMonth.getMonthValue();
        List<Long> preparedUserIds = new ArrayList<>();
        int created = 0;
        int skipped = 0;

        for (Long userId : userIds) {
            var existing = reports.findByUser_UserIdAndReportYearAndReportMonth(userId, year, month);
            if (existing.isPresent()) {
                MonthlyReport report = existing.get();
                if (report.getAiRecapStatus() == MonthlyReport.AiRecapStatus.FAILED) {
                    // 기존 월간 집계를 보존하고 실패한 AI 회고만 다시 요청한다.
                    report.retryFailedRecap();
                    preparedUserIds.add(userId);
                    continue;
                }
                if (report.getAiRecapStatus() == MonthlyReport.AiRecapStatus.PENDING) {
                    // 앞선 집계 묶음의 커밋 결과를 재실행 시 AI 요청 대상에 다시 포함한다.
                    preparedUserIds.add(userId);
                    continue;
                }
                skipped++;
                continue;
            }

            var summary = records.findMonthlyRecordSummary(userId, start, end).orElseThrow();
            if (summary.recordCount() == 0L) {
                // 대상 ID를 읽은 뒤 기간 기록이 모두 삭제된 경우 빈 평균값과 리포트를 저장하지 않는다.
                skipped++;
                continue;
            }
            var topPlace = records.findMonthlyTopPlaces(userId, start, end, PageRequest.of(0, 1));
            var topArtist = records.findMonthlyTopArtists(userId, start, end, PageRequest.of(0, 1));
            String artist = topArtist.isEmpty() ? null : topArtist.getFirst().artistName();
            MonthlyReport report = MonthlyReport.prepare(users.getReferenceById(userId), year,
                    month, summary.recordCount().intValue(), artist);
            if (!topPlace.isEmpty()) {
                // 대표 법정동 안의 장소 행을 기존 top_place_id FK에 연결한다.
                report.assignTopPlace(places.getReferenceById(topPlace.getFirst().placeId()));
            }
            MonthlyReport saved = reports.save(report);
            // DB의 소수 첫째 자리 평균에 맞춰 중간값 이상은 올림으로 반올림한다.
            moods.save(MonthlyMoodStat.create(saved,
                    BigDecimal.valueOf(summary.averageMoodScore()).setScale(1, RoundingMode.HALF_UP)));
            preparedUserIds.add(userId);
            created++;
        }

        return new PreparedChunk(List.copyOf(preparedUserIds), created, skipped);
    }

    public record PreparedChunk(List<Long> userIds, int createdCount, int skippedCount) { }
}
