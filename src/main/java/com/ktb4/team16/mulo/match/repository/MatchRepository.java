package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.match.entity.Match;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT match FROM Match match WHERE match.matchId = :matchId")
    Optional<Match> findByMatchIdForUpdate(@Param("matchId") Long matchId);
}
