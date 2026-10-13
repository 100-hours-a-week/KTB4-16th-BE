package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
}
