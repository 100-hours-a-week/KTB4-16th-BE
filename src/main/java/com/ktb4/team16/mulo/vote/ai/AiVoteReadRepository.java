package com.ktb4.team16.mulo.vote.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiVoteReadRepository {
    boolean isAvailable();

    Map<Long, String> findAllUserGenres();

    Optional<String> findUserGenre(Long userId);

    List<AiVoteQuestion> findQuestions(LocalDate fromDate, LocalDate toDate);
}
