package com.ktb4.team16.mulo.vote.snapshot;

import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteDailySnapshotWriter {

    private final VoteSnapshotStore store;

    @Transactional
    public void write(LocalDate voteDate, List<AiVoteQuestion> questions,
            List<VoteUserGenreAssignment> users) {
        if (store.isReady(voteDate)) {
            return;
        }

        store.beginSnapshot(voteDate);
        Map<String, Long> questionIds = store.saveQuestions(voteDate, questions);
        List<VoteSnapshotStore.AssignmentRow> assignments = users.stream()
                .map(user -> new VoteSnapshotStore.AssignmentRow(user.userId(), user.musicGenre(),
                        questionIds.get(user.musicGenre())))
                .toList();
        store.saveAssignments(voteDate, assignments);
        store.markReady(voteDate);
    }
}
