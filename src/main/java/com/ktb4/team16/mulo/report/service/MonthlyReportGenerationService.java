package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.report.client.MonthlyReportAiClient;
import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
import com.ktb4.team16.mulo.report.entity.MonthlyPhotoSceneStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportGenerationService {
    private final RecordRepository records; private final PlaceRepository places; private final UserRepository users; private final MonthlyReportRepository reports;
    private final MonthlyMoodStatRepository moods; private final MonthlyPhotoSceneStatRepository scenes; private final MonthlyReportAiClient ai; private final Clock clock;
    public MonthlyReportGenerationService(RecordRepository records, PlaceRepository places, UserRepository users, MonthlyReportRepository reports, MonthlyMoodStatRepository moods, MonthlyPhotoSceneStatRepository scenes, MonthlyReportAiClient ai, Clock clock) { this.records=records;this.places=places;this.users=users;this.reports=reports;this.moods=moods;this.scenes=scenes;this.ai=ai;this.clock=clock; }
    // 현재 KST 기준으로 완료된 지난달 리포트를 생성한다.
    @Transactional public GenerationResult generatePreviousMonthReports(){return generate(YearMonth.now(clock).minusMonths(1));}
    // 지정 월의 활성 기록을 한 번만 스냅샷으로 저장한다.
    @Transactional public GenerationResult generate(YearMonth month){
        LocalDateTime start=month.atDay(1).atStartOfDay(), end=month.plusMonths(1).atDay(1).atStartOfDay(); int created=0, skipped=0;
        for(Long userId:records.findUsersWithActiveRecordsInPeriod(start,end)){
            short year = (short) month.getYear(); short monthValue = (short) month.getMonthValue();
            if(reports.existsByUser_UserIdAndReportYearAndReportMonth(userId,year,monthValue)){skipped++;continue;}
            var summary=records.findMonthlyRecordSummary(userId,start,end).orElseThrow();
            var topPlace=records.findMonthlyTopPlaces(userId,start,end,PageRequest.of(0,1));
            var topArtist=records.findMonthlyTopArtists(userId,start,end,PageRequest.of(0,1)); String artist=topArtist.isEmpty()?null:topArtist.getFirst().artistName();
            var result=ai.generate(month.getYear(),month.getMonthValue(),artist,false);
            var report=MonthlyReport.create(users.getReferenceById(userId),year,monthValue,summary.recordCount().intValue(),artist,result.recapText());
            if (!topPlace.isEmpty()) report.assignTopPlace(places.getReferenceById(topPlace.getFirst().placeId()));
            var savedReport = reports.save(report);
            moods.save(MonthlyMoodStat.create(savedReport,BigDecimal.valueOf(summary.averageMoodScore()).setScale(1)));
            result.scenes().forEach(scene -> scenes.save(MonthlyPhotoSceneStat.create(
                    savedReport, scene.tag(), scene.count(), (byte) scene.ratio()))); created++;
        } return new GenerationResult(month,created,skipped);
    }
    public record GenerationResult(YearMonth targetMonth,int createdCount,int skippedCount){}
}
