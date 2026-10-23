package com.ktb4.team16.mulo.vote.snapshot;

import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.scheduler.VoteDailySnapshotService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteDailySnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(VoteDailySnapshotWriter.class);
    private static final String COMMON_GENRE = "공통";

    private final VoteSnapshotStore store;

    @Transactional
    public void finalizeSnapshot(LocalDate voteDate, List<AiVoteQuestion> latestQuestions,
            List<Long> activeUserIds, Optional<Map<Long, String>> latestUserGenres) {
        if (store.beginOrLockSnapshot(voteDate)) {
            return;
        }

        Map<String, Long> questionIds = new LinkedHashMap<>(store.saveQuestions(voteDate, latestQuestions));

        Map<Long, String> genresByUser = new LinkedHashMap<>();
        for (VoteSnapshotStore.AssignmentRow assignment : store.findAssignments(voteDate)) {
            genresByUser.put(assignment.userId(), assignment.musicGenre());
        }
        long usersMissingPrefetchedGenre = activeUserIds.stream()
                .filter(userId -> !genresByUser.containsKey(userId))
                .count();
        if (usersMissingPrefetchedGenre > 0 && latestUserGenres.isEmpty()) {
            log.warn("AI 사용자 장르 조회 실패로 사전 배정 없는 사용자를 공통 그룹에 배정합니다. date={}, count={}",
                    voteDate, usersMissingPrefetchedGenre);
        } else if (usersMissingPrefetchedGenre > 0) {
            long usersWithoutAiRow = activeUserIds.stream()
                    .filter(userId -> !genresByUser.containsKey(userId))
                    .filter(userId -> !latestUserGenres.orElseThrow().containsKey(userId))
                    .count();
            if (usersWithoutAiRow > 0) {
                log.info("AI 사용자 장르 조회는 성공했으나 행이 없어 공통 그룹에 배정합니다. date={}, count={}",
                        voteDate, usersWithoutAiRow);
            }
        }
        for (Long userId : activeUserIds) {
            if (genresByUser.containsKey(userId)) {
                continue;
            }
            String genre = latestUserGenres.map(availableGenres -> availableGenres.get(userId))
                    .orElse(COMMON_GENRE);
            genresByUser.put(userId, genre == null ? COMMON_GENRE : genre);
        }
        genresByUser.values().forEach(VoteDailySnapshotService::validateGenre);

        Set<String> requiredGenres = new LinkedHashSet<>(genresByUser.values());
        if (!store.hasQuestions(voteDate)) {
            log.error("당일 질문이 없어 취향 투표 스냅샷 확정을 중단합니다. date={}", voteDate);
            throw new IllegalStateException("당일 질문이 없어 스냅샷을 확정할 수 없습니다.");
        }
        Long commonQuestionId = questionIds.computeIfAbsent(COMMON_GENRE,
                key -> store.findQuestionId(voteDate, key).orElse(null));
        for (String genre : requiredGenres) {
            Long questionId = questionIds.computeIfAbsent(genre,
                    key -> store.findQuestionId(voteDate, key).orElse(null));
            if (questionId == null) {
                questionId = commonQuestionId;
            }
            if (questionId == null) {
                log.error("사용자 배정 그룹 및 공통 그룹의 당일 질문이 없어 스냅샷 확정을 중단합니다. date={}, genre={}",
                        voteDate, genre);
                throw new IllegalStateException("사용자 배정 그룹의 질문이 없어 스냅샷을 확정할 수 없습니다.");
            }
            questionIds.put(genre, questionId);
        }
        List<VoteSnapshotStore.AssignmentRow> assignments = genresByUser.entrySet().stream()
                .map(entry -> new VoteSnapshotStore.AssignmentRow(entry.getKey(), entry.getValue(),
                        questionIds.get(entry.getValue())))
                .toList();
        store.completeAssignments(voteDate, assignments);
        if (store.hasAssignmentsWithoutQuestion(voteDate)) {
            log.error("질문 ID가 없는 사용자 배정이 남아 스냅샷 확정을 중단합니다. date={}", voteDate);
            throw new IllegalStateException("질문 ID가 없는 사용자 배정이 있어 스냅샷을 확정할 수 없습니다.");
        }
        store.markReady(voteDate);
    }
}
