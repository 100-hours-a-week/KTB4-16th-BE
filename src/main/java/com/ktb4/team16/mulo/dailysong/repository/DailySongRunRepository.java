package com.ktb4.team16.mulo.dailysong.repository;

import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySongRunRepository extends JpaRepository<DailySongRun, LocalDate> {
}
