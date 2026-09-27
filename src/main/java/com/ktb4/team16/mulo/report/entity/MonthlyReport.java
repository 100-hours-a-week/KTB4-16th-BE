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

    private MonthlyReport(User user, short year, short month, int count, String artist, String recap) {
        this.user = user; this.reportYear = year; this.reportMonth = month; this.recordCount = count;
        this.topArtistName = artist; this.aiRecapText = recap; this.aiRecapStatus = AiRecapStatus.COMPLETED;
    }
    // 집계와 AI 회고가 완료된 월간 스냅샷을 생성한다.
    public static MonthlyReport create(User user, short year, short month, int count, String artist, String recap) {
        return new MonthlyReport(user, year, month, count, artist, recap);
    }
    // 대표 장소를 집계 결과로 연결한다.
    public void assignTopPlace(Place topPlace) { this.topPlace = topPlace; }
    public enum AiRecapStatus { PENDING, PROCESSING, COMPLETED, FAILED }
}
