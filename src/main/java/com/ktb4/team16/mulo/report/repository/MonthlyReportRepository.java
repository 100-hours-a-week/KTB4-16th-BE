package com.ktb4.team16.mulo.report.repository;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
    boolean existsByUser_UserIdAndReportYearAndReportMonth(Long userId, short year, short month);

    List<MonthlyReport> findByUser_UserIdOrderByReportYearDescReportMonthDesc(Long userId);

    Optional<MonthlyReport> findByMonthlyReportIdAndUser_UserId(Long monthlyReportId, Long userId);

    Optional<MonthlyReport> findByUser_UserIdAndReportYearAndReportMonth(Long userId, short year,
            short month);

    List<MonthlyReport> findByReportYearAndReportMonth(short year, short month);
}
