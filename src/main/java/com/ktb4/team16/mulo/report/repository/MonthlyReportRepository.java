package com.ktb4.team16.mulo.report.repository;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
    boolean existsByUser_UserIdAndReportYearAndReportMonth(Long userId, short year, short month);

    List<MonthlyReport> findByUser_UserIdOrderByReportYearDescReportMonthDesc(Long userId);

    Optional<MonthlyReport> findByMonthlyReportIdAndUser_UserId(Long monthlyReportId, Long userId);

    // 상세 응답에 필요한 대표 장소를 리포트와 함께 읽어 추가 지연 로딩 조회를 피한다.
    @EntityGraph(attributePaths = "topPlace")
    @Query("SELECT r FROM MonthlyReport r WHERE r.monthlyReportId = :reportId AND r.user.userId = :userId")
    Optional<MonthlyReport> findByMonthlyReportIdAndUser_UserIdWithTopPlace(
            @Param("reportId") Long reportId, @Param("userId") Long userId);

    Optional<MonthlyReport> findByUser_UserIdAndReportYearAndReportMonth(Long userId, short year,
            short month);

    List<MonthlyReport> findByReportYearAndReportMonth(short year, short month);
}
