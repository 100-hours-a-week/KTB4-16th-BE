package com.ktb4.team16.mulo.vote.snapshot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
class VoteDailySnapshotWriterTest {
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 17);

    @Mock VoteSnapshotStore store;

    private VoteDailySnapshotWriter writer;

    @BeforeEach
    void setUp() {
        writer = new VoteDailySnapshotWriter(store);
    }

    @Test
    void usesPrefetchedQuestionAndGenreWhenFinalAiReadsFailed() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        when(store.findQuestionContent(TARGET_DATE, "재즈"))
                .thenReturn(Optional.of(new VoteQuestionContent("재즈", "저장 질문", "A", "B")));
        when(store.saveQuestions(TARGET_DATE, List.of())).thenReturn(Map.of());
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);
        when(store.findQuestionId(TARGET_DATE, "재즈")).thenReturn(Optional.of(41L));

        writer.finalizeSnapshot(TARGET_DATE, List.of(), List.of(17L), Optional.empty());

        verify(store).completeAssignments(TARGET_DATE,
                List.of(new VoteSnapshotStore.AssignmentRow(17L, "재즈", 41L)));
        verify(store).markReady(TARGET_DATE);
    }

    @Test
    void usesSameDayCommonQuestionForMissingAssignedGroupsAndSharesItsId() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null),
                new VoteSnapshotStore.AssignmentRow(18L, "댄스", null)));
        AiVoteQuestion commonQuestion = new AiVoteQuestion(TARGET_DATE, "공통", "공통 질문", "A", "B");
        when(store.saveQuestions(TARGET_DATE, List.of(commonQuestion))).thenReturn(Map.of("공통", 42L));
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);

        writer.finalizeSnapshot(TARGET_DATE, List.of(commonQuestion), List.of(17L, 18L), Optional.empty());

        verify(store).completeAssignments(TARGET_DATE, List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", 42L),
                new VoteSnapshotStore.AssignmentRow(18L, "댄스", 42L)));
        verify(store).markReady(TARGET_DATE);
        verify(store, never()).findQuestionContent(TARGET_DATE.minusDays(1), "재즈");
        verify(store, never()).findQuestionContent(TARGET_DATE.minusDays(1), "댄스");
    }

    @Test
    void assignsCommonToUserWithoutPrefetchedGenreWhenFinalAiReadFailed() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(store.saveQuestions(TARGET_DATE, List.of())).thenReturn(Map.of());
        when(store.findQuestionContent(TARGET_DATE, "공통"))
                .thenReturn(Optional.of(new VoteQuestionContent("공통", "사전 질문", "A", "B")));
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);
        when(store.findQuestionId(TARGET_DATE, "공통")).thenReturn(Optional.of(43L));

        writer.finalizeSnapshot(TARGET_DATE, List.of(), List.of(19L), Optional.empty());

        verify(store).completeAssignments(TARGET_DATE,
                List.of(new VoteSnapshotStore.AssignmentRow(19L, "공통", 43L)));
        verify(store).markReady(TARGET_DATE);
    }

    @Test
    void doesNotMarkSnapshotReadyWhenNoQuestionExistsForToday() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(store.saveQuestions(TARGET_DATE, List.of())).thenReturn(Map.of());
        when(store.hasQuestions(TARGET_DATE)).thenReturn(false);

        assertThatThrownBy(() -> writer.finalizeSnapshot(
                TARGET_DATE, List.of(), List.of(19L), Optional.empty()))
                .isInstanceOf(IllegalStateException.class);

        verify(store, never()).completeAssignments(ArgumentMatchers.eq(TARGET_DATE), ArgumentMatchers.anyList());
        verify(store, never()).markReady(TARGET_DATE);
    }

    @Test
    void doesNotMarkSnapshotReadyWhenARequiredGroupAndCommonQuestionAreMissing() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null),
                new VoteSnapshotStore.AssignmentRow(18L, "공통", null)));
        when(store.saveQuestions(TARGET_DATE, List.of())).thenReturn(Map.of());
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);
        when(store.findQuestionId(TARGET_DATE, "재즈")).thenReturn(Optional.of(41L));
        when(store.findQuestionId(TARGET_DATE, "공통")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.finalizeSnapshot(
                TARGET_DATE, List.of(), List.of(17L, 18L), Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("사용자 배정 그룹의 질문이 없어 스냅샷을 확정할 수 없습니다.");

        verify(store, never()).completeAssignments(ArgumentMatchers.eq(TARGET_DATE), ArgumentMatchers.anyList());
        verify(store, never()).markReady(TARGET_DATE);
    }

    @Test
    void prefersAssignedGenreQuestionOverSameDayCommonQuestion() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        AiVoteQuestion jazzQuestion = new AiVoteQuestion(TARGET_DATE, "재즈", "재즈 질문", "A", "B");
        AiVoteQuestion commonQuestion = new AiVoteQuestion(TARGET_DATE, "공통", "공통 질문", "A", "B");
        when(store.saveQuestions(TARGET_DATE, List.of(jazzQuestion, commonQuestion)))
                .thenReturn(Map.of("재즈", 41L, "공통", 42L));
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);

        writer.finalizeSnapshot(TARGET_DATE, List.of(jazzQuestion, commonQuestion), List.of(17L), Optional.empty());

        verify(store).completeAssignments(TARGET_DATE,
                List.of(new VoteSnapshotStore.AssignmentRow(17L, "재즈", 41L)));
        verify(store).markReady(TARGET_DATE);
    }

    @Test
    void doesNotMarkSnapshotReadyWhenAnyPersistedAssignmentStillHasNoQuestionId() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(false);
        when(store.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        when(store.findQuestionContent(TARGET_DATE, "재즈"))
                .thenReturn(Optional.of(new VoteQuestionContent("재즈", "오늘 재즈 질문", "A", "B")));
        when(store.saveQuestions(TARGET_DATE, List.of())).thenReturn(Map.of());
        when(store.hasQuestions(TARGET_DATE)).thenReturn(true);
        when(store.findQuestionId(TARGET_DATE, "재즈")).thenReturn(Optional.of(41L));
        when(store.hasAssignmentsWithoutQuestion(TARGET_DATE)).thenReturn(true);

        assertThatThrownBy(() -> writer.finalizeSnapshot(
                TARGET_DATE, List.of(), List.of(17L), Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("질문 ID가 없는 사용자 배정이 있어 스냅샷을 확정할 수 없습니다.");

        verify(store).completeAssignments(TARGET_DATE,
                List.of(new VoteSnapshotStore.AssignmentRow(17L, "재즈", 41L)));
        verify(store, never()).markReady(TARGET_DATE);
    }

    @Test
    void doesNotChangeReadySnapshotWhenFinalizationIsRetried() {
        when(store.beginOrLockSnapshot(TARGET_DATE)).thenReturn(true);

        writer.finalizeSnapshot(TARGET_DATE, List.of(), List.of(19L), Optional.empty());

        verify(store).beginOrLockSnapshot(TARGET_DATE);
        verify(store, never()).saveQuestions(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
        verify(store, never()).markReady(TARGET_DATE);
    }
}
