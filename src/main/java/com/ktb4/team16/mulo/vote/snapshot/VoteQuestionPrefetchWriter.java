package com.ktb4.team16.mulo.vote.snapshot;

import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteQuestionPrefetchWriter {

    private final VoteSnapshotStore store;

    @Transactional
    public void save(LocalDate targetDate, List<AiVoteQuestion> questions,
            List<VoteSnapshotStore.AssignmentRow> assignments) {
        if (questions.isEmpty() && assignments.isEmpty()) {
            return;
        }
        if (store.beginOrLockSnapshot(targetDate)) {
            return;
        }
        store.saveQuestions(targetDate, questions);
        store.savePrefetchedAssignments(targetDate, assignments);
    }
}
