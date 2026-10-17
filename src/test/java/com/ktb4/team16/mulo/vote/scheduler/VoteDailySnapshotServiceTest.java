package com.ktb4.team16.mulo.vote.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailySnapshotWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import com.ktb4.team16.mulo.vote.snapshot.VoteQuestionContent;
import com.ktb4.team16.mulo.vote.snapshot.VoteUserGenreAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoteDailySnapshotServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock AiVoteReadRepository aiRepository;
    @Mock UserRepository userRepository;
    @Mock VoteSnapshotStore snapshotStore;
    @Mock VoteDailySnapshotWriter snapshotWriter;

    private VoteDailySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new VoteDailySnapshotService(aiRepository, userRepository,
                snapshotStore, snapshotWriter,
                java.time.Clock.fixed(java.time.Instant.parse("2026-10-15T15:00:00Z"),
                        java.time.ZoneId.of("Asia/Seoul")));
    }

    @Test
    void usesYesterdayQuestionForSameGroupAndStoresItAsToday() {
        User user = user(17L, List.of("재즈"));
        when(snapshotStore.isReady(TODAY)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        when(aiRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY.minusDays(1), "재즈", "전날 질문", "A", "B")));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));

        service.createSnapshot(TODAY);

        ArgumentCaptor<List<AiVoteQuestion>> questions = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<VoteUserGenreAssignment>> assignments = ArgumentCaptor.forClass(List.class);
        verify(snapshotWriter).write(org.mockito.ArgumentMatchers.eq(TODAY),
                questions.capture(), assignments.capture());
        assertThat(questions.getValue()).contains(new AiVoteQuestion(TODAY, "재즈", "전날 질문", "A", "B"));
        assertThat(assignments.getValue()).contains(new VoteUserGenreAssignment(17L, "재즈"));
    }

    @Test
    void snapshotsUsersWithoutAiRowsAsCommonWithoutInferringTheirInterest() {
        User noInterest = user(17L, null);
        User hasHistory = user(18L, null);
        when(snapshotStore.isReady(TODAY)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of());
        when(aiRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(noInterest, hasHistory));

        service.createSnapshot(TODAY);

        ArgumentCaptor<List<VoteUserGenreAssignment>> assignments = ArgumentCaptor.forClass(List.class);
        verify(snapshotWriter).write(org.mockito.ArgumentMatchers.eq(TODAY),
                org.mockito.ArgumentMatchers.anyList(), assignments.capture());
        assertThat(assignments.getValue()).containsExactly(
                new VoteUserGenreAssignment(17L, "공통"),
                new VoteUserGenreAssignment(18L, "공통"));
    }

    @Test
    void doesNothingWhenSnapshotIsAlreadyReady() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);

        service.createSnapshot(TODAY);

        verify(aiRepository, never()).findAllUserGenres();
        verify(snapshotWriter, never()).write(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void leavesSnapshotUnpublishedWhenAiDatabaseIsNotConfigured() {
        when(snapshotStore.isReady(TODAY)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(false);

        service.createSnapshot(TODAY);

        verify(snapshotWriter, never()).write(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void reusesExistingMysqlQuestionBeforeReadingAiQuestions() {
        User user = user(17L, List.of("재즈"));
        when(snapshotStore.isReady(TODAY)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));
        when(snapshotStore.findQuestionContent(TODAY, "재즈"))
                .thenReturn(java.util.Optional.of(
                        new VoteQuestionContent("재즈", "BE 기존 질문", "기존 A", "기존 B")));

        service.createSnapshot(TODAY);

        ArgumentCaptor<List<AiVoteQuestion>> questions = ArgumentCaptor.forClass(List.class);
        verify(snapshotWriter).write(org.mockito.ArgumentMatchers.eq(TODAY),
                questions.capture(), org.mockito.ArgumentMatchers.anyList());
        assertThat(questions.getValue()).containsExactly(
                new AiVoteQuestion(TODAY, "재즈", "BE 기존 질문", "기존 A", "기존 B"));
        verify(aiRepository, never()).findQuestions(TODAY.minusDays(1), TODAY);
    }

    @Test
    void doesNotPublishSnapshotWhenMissingQuestionCannotBeReadFromAi() {
        User user = user(17L, List.of("재즈"));
        when(snapshotStore.isReady(TODAY)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));
        when(aiRepository.findQuestions(TODAY.minusDays(1), TODAY))
                .thenThrow(new IllegalStateException("AI question view unavailable"));

        service.createSnapshot(TODAY);

        verify(snapshotWriter, never()).write(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    private User user(Long id, List<String> genres) {
        User user = User.signup("u" + id + "@example.com", "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "userId", id);
        ReflectionTestUtils.setField(user, "preferredGenres", genres);
        return user;
    }
}
