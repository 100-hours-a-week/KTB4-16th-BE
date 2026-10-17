package com.ktb4.team16.mulo.vote.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        prefix = "mulo.ai-db", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableAiVoteReadRepository implements AiVoteReadRepository {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Map<Long, String> findAllUserGenres() {
        throw new IllegalStateException("AI DB integration is not configured.");
    }

    @Override
    public Optional<String> findUserGenre(Long userId) {
        throw new IllegalStateException("AI DB integration is not configured.");
    }

    @Override
    public List<AiVoteQuestion> findQuestions(LocalDate fromDate, LocalDate toDate) {
        throw new IllegalStateException("AI DB integration is not configured.");
    }
}
