package com.ktb4.team16.mulo.report.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor;
@Entity @Table(name="monthly_photo_scene_stats") @Getter @NoArgsConstructor
public class MonthlyPhotoSceneStat {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long photoSceneStatId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="monthly_report_id", nullable=false) private MonthlyReport monthlyReport;
 @Column(nullable=false) private String sceneTag; @Column(nullable=false) private int count; @Column(nullable=false) private Byte ratio;
 private MonthlyPhotoSceneStat(MonthlyReport report,String tag,int count,Byte ratio){this.monthlyReport=report;this.sceneTag=tag;this.count=count;this.ratio=ratio;}
 // AI가 반환한 사진 장면 통계를 월간 스냅샷에 연결한다.
 public static MonthlyPhotoSceneStat create(MonthlyReport report,String tag,int count,Byte ratio){return new MonthlyPhotoSceneStat(report,tag,count,ratio);}
}
