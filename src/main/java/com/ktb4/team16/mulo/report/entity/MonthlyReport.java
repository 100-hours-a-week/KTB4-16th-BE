package com.ktb4.team16.mulo.report.entity;

import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "monthly_reports") @Getter @NoArgsConstructor
public class MonthlyReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long monthlyReportId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "report_year", nullable = false) private short reportYear;
    @Column(name = "report_month", nullable = false) private short reportMonth;
    @Column(nullable = false) private int recordCount;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "top_place_id") private Place topPlace;
    private String topArtistName;
    private String aiRecapText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AiRecapStatus aiRecapStatus;
    @Column(insertable = false, updatable = false) private LocalDateTime createdAt;

    private MonthlyReport(User user, short year, short month, int count, String artist,
            String recap, AiRecapStatus recapStatus) {
        this.user = user;
        this.reportYear = year;
        this.reportMonth = month;
        this.recordCount = count;
        this.topArtistName = artist;
        this.aiRecapText = recap;
        this.aiRecapStatus = recapStatus;
    }

    // 집계와 AI 회고가 완료된 월간 스냅샷을 생성한다.
    public static MonthlyReport create(User user, short year, short month, int count, String artist, String recap) {
        return new MonthlyReport(user, year, month, count, artist, recap, AiRecapStatus.COMPLETED);
    }

    // AI 생성 전 백엔드 집계만 저장한 월간 리포트를 PENDING 상태로 만든다.
    public static MonthlyReport prepare(User user, short year, short month, int count, String artist) {
        return new MonthlyReport(user, year, month, count, artist, null, AiRecapStatus.PENDING);
    }

    // AI Gateway가 배치 생성 요청을 접수했음을 리포트 상태에 반영한다.
    public void markProcessing() {
        if (aiRecapStatus == AiRecapStatus.PENDING) {
            aiRecapStatus = AiRecapStatus.PROCESSING;
        }
    }

    // AI가 제공한 회고를 저장하고 리포트 생성을 완료 상태로 전환한다.
    public void complete(String recap) {
        aiRecapText = recap;
        aiRecapStatus = AiRecapStatus.COMPLETED;
    }

    // AI 실패는 아직 완료되지 않은 리포트만 FAILED로 전환해 완료 결과를 보존한다.
    public void markFailedUnlessCompleted() {
        if (aiRecapStatus != AiRecapStatus.COMPLETED) {
            aiRecapStatus = AiRecapStatus.FAILED;
        }
    }
    // 대표 장소를 집계 결과로 연결한다.
    public void assignTopPlace(Place topPlace) { this.topPlace = topPlace; }
    public enum AiRecapStatus { PENDING, PROCESSING, COMPLETED, FAILED }
}
