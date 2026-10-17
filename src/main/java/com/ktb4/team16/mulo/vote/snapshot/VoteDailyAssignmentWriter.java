package com.ktb4.team16.mulo.vote.snapshot;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteDailyAssignmentWriter {

    private final VoteSnapshotStore store;

    @Transactional
    public VoteDailyAssignment assignIfMissing(LocalDate voteDate, Long userId,
            String musicGenre, Long questionId) {
        if (!store.isReady(voteDate)) {
            return null;
        }
        store.assignIfMissing(voteDate, userId, musicGenre, questionId);
        return store.findAssignment(voteDate, userId).orElseThrow();
    }
}
