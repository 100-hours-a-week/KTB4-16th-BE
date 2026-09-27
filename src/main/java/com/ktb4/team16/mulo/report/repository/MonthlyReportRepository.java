package com.ktb4.team16.mulo.report.repository;
import com.ktb4.team16.mulo.report.entity.MonthlyReport; import org.springframework.data.jpa.repository.JpaRepository;
public interface MonthlyReportRepository extends JpaRepository<MonthlyReport,Long> { boolean existsByUser_UserIdAndReportYearAndReportMonth(Long userId,short year,short month); }
