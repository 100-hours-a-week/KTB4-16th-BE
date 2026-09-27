package com.ktb4.team16.mulo.report.entity;
import jakarta.persistence.*; import java.math.BigDecimal; import lombok.Getter; import lombok.NoArgsConstructor;
@Entity @Table(name = "monthly_mood_stats") @Getter @NoArgsConstructor
public class MonthlyMoodStat {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long moodStatId;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="monthly_report_id", nullable=false) private MonthlyReport monthlyReport;
 @Column(nullable=false, precision=3, scale=1) private BigDecimal averageMoodScore;
 private MonthlyMoodStat(MonthlyReport report, BigDecimal score){this.monthlyReport=report;this.averageMoodScore=score;}
 // 월간 평균 기분 스냅샷을 저장한다.
 public static MonthlyMoodStat create(MonthlyReport report, BigDecimal score){return new MonthlyMoodStat(report,score);}
}
