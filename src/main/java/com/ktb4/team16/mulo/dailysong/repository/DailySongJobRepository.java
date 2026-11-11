package com.ktb4.team16.mulo.dailysong.repository;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySongJobRepository extends JpaRepository<DailySongJob, Long> {
    Optional<DailySongJob> findByRecommendationDateAndUserId(LocalDate recommendationDate, Long userId);

    @EntityGraph(attributePaths = "musicTrack")
    Optional<DailySongJob> findByJobIdAndUserIdAndStatus(Long jobId, Long userId, DailySongJobStatus status);
}
