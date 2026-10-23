package com.ktb4.team16.mulo.vote.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.user.domain.PreferredGenre;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteQuestionContent;
import com.ktb4.team16.mulo.vote.snapshot.VoteQuestionPrefetchWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoteQuestionPrefetchServiceTest {
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 17);

    @Mock AiVoteReadRepository aiVoteReadRepository;
    @Mock UserRepository userRepository;
    @Mock VoteSnapshotStore snapshotStore;
    @Mock VoteQuestionPrefetchWriter prefetchWriter;

    private VoteQuestionPrefetchService service;

    @BeforeEach
    void setUp() {
        service = new VoteQuestionPrefetchService(aiVoteReadRepository, userRepository, snapshotStore,
                prefetchWriter, Clock.fixed(Instant.parse("2026-10-16T14:45:00Z"),
                        ZoneId.of("Asia/Seoul")));
    }

    @Test
    void schedulesQuestionAndGenrePrefetchAt2345And2350KstForTomorrow() throws Exception {
        Scheduled prefetchSchedule = VoteQuestionPrefetchService.class
                .getMethod("prefetchTomorrowQuestions").getAnnotation(Scheduled.class);
        Scheduled retrySchedule = VoteQuestionPrefetchService.class
                .getMethod("retryTomorrowQuestions").getAnnotation(Scheduled.class);
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(true);

        service.prefetchTomorrowQuestions();

        org.assertj.core.api.Assertions.assertThat(prefetchSchedule.cron()).isEqualTo("0 45 23 * * *");
        org.assertj.core.api.Assertions.assertThat(prefetchSchedule.zone()).isEqualTo("Asia/Seoul");
        org.assertj.core.api.Assertions.assertThat(retrySchedule.cron()).isEqualTo("0 50 23 * * *");
        org.assertj.core.api.Assertions.assertThat(retrySchedule.zone()).isEqualTo("Asia/Seoul");
        verify(aiVoteReadRepository, never()).findQuestions(TARGET_DATE, TARGET_DATE);
        verify(aiVoteReadRepository, never()).findAllUserGenres();
    }

    @Test
    void skipsAiReadsAtRetryWhenQuestionsAndUserAssignmentsWereFullyPrefetched() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "공통", null)));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));
        for (PreferredGenre genre : PreferredGenre.values()) {
            when(snapshotStore.findQuestionContent(TARGET_DATE, genre.value()))
                    .thenReturn(Optional.of(new VoteQuestionContent(genre.value(), "질문", "A", "B")));
        }
        when(snapshotStore.findQuestionContent(TARGET_DATE, "공통"))
                .thenReturn(Optional.of(new VoteQuestionContent("공통", "질문", "A", "B")));

        service.retryTomorrowQuestions();

        verify(aiVoteReadRepository, never()).isAvailable();
        verify(aiVoteReadRepository, never()).findQuestions(TARGET_DATE, TARGET_DATE);
        verify(aiVoteReadRepository, never()).findAllUserGenres();
        verify(prefetchWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void skipsAllAiReadsAt2350After2345PersistedAllQuestionsAndAssignments() {
        AtomicBoolean persisted = new AtomicBoolean();
        User user = user(17L);
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenAnswer(invocation -> persisted.get()
                ? List.of(new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)) : List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));
        for (String genre : allGenres()) {
            when(snapshotStore.findQuestionContent(TARGET_DATE, genre)).thenAnswer(invocation ->
                    persisted.get()
                            ? Optional.of(new VoteQuestionContent(genre, "질문", "A", "B"))
                            : Optional.empty());
        }
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(allQuestions());
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        org.mockito.Mockito.doAnswer(invocation -> {
            persisted.set(true);
            return null;
        }).when(prefetchWriter).save(org.mockito.ArgumentMatchers.eq(TARGET_DATE),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());

        service.prefetchTomorrowQuestions();
        service.retryTomorrowQuestions();

        verify(aiVoteReadRepository, org.mockito.Mockito.times(2)).isAvailable();
        verify(aiVoteReadRepository).findQuestions(TARGET_DATE, TARGET_DATE);
        verify(aiVoteReadRepository).findAllUserGenres();
        verify(prefetchWriter).save(TARGET_DATE, allQuestions(),
                List.of(new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        org.mockito.Mockito.verifyNoMoreInteractions(aiVoteReadRepository);
    }

    @Test
    void retriesAiReadsAfterTheInitialAttemptFailedCompletely() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenThrow(new IllegalStateException("AI view unavailable"));
        when(aiVoteReadRepository.findAllUserGenres())
                .thenThrow(new IllegalStateException("AI view unavailable"));

        service.prefetchTomorrowQuestions();
        service.retryTomorrowQuestions();

        verify(aiVoteReadRepository, org.mockito.Mockito.times(2))
                .findQuestions(TARGET_DATE, TARGET_DATE);
        verify(aiVoteReadRepository, org.mockito.Mockito.times(2)).findAllUserGenres();
        verify(prefetchWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void retriesOnlyMissingQuestionsWhenInitialPrefetchWasPartial() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "공통", null)));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));
        for (PreferredGenre genre : PreferredGenre.values()) {
            when(snapshotStore.findQuestionContent(TARGET_DATE, genre.value()))
                    .thenReturn(Optional.of(new VoteQuestionContent(genre.value(), "질문", "A", "B")));
        }
        when(snapshotStore.findQuestionContent(TARGET_DATE, "공통"))
                .thenReturn(Optional.empty());
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(question(TARGET_DATE, "공통", "보완 질문")));

        service.retryTomorrowQuestions();

        verify(aiVoteReadRepository).findQuestions(TARGET_DATE, TARGET_DATE);
        verify(aiVoteReadRepository, never()).findAllUserGenres();
        verify(prefetchWriter).save(TARGET_DATE,
                List.of(question(TARGET_DATE, "공통", "보완 질문")), List.of());
    }

    @Test
    void keepsPreviouslyPrefetchedDataWhenTheRetryReadFails() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));
        for (PreferredGenre genre : PreferredGenre.values()) {
            when(snapshotStore.findQuestionContent(TARGET_DATE, genre.value()))
                    .thenReturn(Optional.of(new VoteQuestionContent(genre.value(), "기존 질문", "기존 A", "기존 B")));
        }
        when(snapshotStore.findQuestionContent(TARGET_DATE, "공통")).thenReturn(Optional.empty());
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenThrow(new IllegalStateException("AI question view unavailable"));

        service.retryTomorrowQuestions();

        verify(snapshotStore, org.mockito.Mockito.atLeastOnce())
                .findQuestionContent(TARGET_DATE, "발라드");
        verify(snapshotStore, org.mockito.Mockito.atLeastOnce())
                .findQuestionContent(TARGET_DATE, "재즈");
        verify(prefetchWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
        verify(aiVoteReadRepository, never()).findAllUserGenres();
    }

    @Test
    void doesNotTreatExistingCommonFallbackAsFailedPrefetchWhenNoNewRowsAreAvailable() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));
        when(snapshotStore.findQuestionContent(TARGET_DATE, "공통"))
                .thenReturn(Optional.of(new VoteQuestionContent("공통", "공통 질문", "A", "B")));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of());

        service.retryTomorrowQuestions();

        verify(prefetchWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
        verify(aiVoteReadRepository, never()).findAllUserGenres();
    }

    @Test
    void prefetchesMissingQuestionsAndGenreAssignmentsWithoutOverwritingExistingAssignments() {
        User existingUser = user(17L);
        User newUser = user(18L);
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(17L, "재즈", null)));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(existingUser, newUser));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of(
                question(TARGET_DATE, "공통", "공통 질문")));
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(17L, "발라드", 18L, "댄스"));

        service.prefetchQuestions(TARGET_DATE);

        verify(prefetchWriter).save(TARGET_DATE,
                List.of(question(TARGET_DATE, "공통", "공통 질문")),
                List.of(new VoteSnapshotStore.AssignmentRow(18L, "댄스", null)));
    }

    @Test
    void missingAiUserRowsArePrefetchedAsCommonWhenTheViewReadSucceeded() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(19L)));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of());
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of());

        service.prefetchQuestions(TARGET_DATE);

        verify(prefetchWriter).save(TARGET_DATE, List.of(),
                List.of(new VoteSnapshotStore.AssignmentRow(19L, "공통", null)));
    }

    @Test
    void retriesQuestionAndUserGenreReadsAndPersistsOnlyNewlyAvailableData() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(20L)));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(), List.of(question(TARGET_DATE, "공통", "질문")));
        when(aiVoteReadRepository.findAllUserGenres()).thenThrow(new IllegalStateException("AI unavailable"))
                .thenReturn(Map.of(20L, "공통"));

        service.prefetchQuestions(TARGET_DATE);
        service.prefetchQuestions(TARGET_DATE);

        verify(prefetchWriter).save(TARGET_DATE,
                List.of(question(TARGET_DATE, "공통", "질문")),
                List.of(new VoteSnapshotStore.AssignmentRow(20L, "공통", null)));
        org.mockito.Mockito.verifyNoMoreInteractions(prefetchWriter);
    }

    @Test
    void doesNotOverwriteStoredGenreOrQuestionDuringRetry() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of(
                new VoteSnapshotStore.AssignmentRow(21L, "재즈", null)));
        when(snapshotStore.findQuestionContent(org.mockito.ArgumentMatchers.eq(TARGET_DATE),
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
                "재즈".equals(invocation.getArgument(1))
                        ? Optional.of(new VoteQuestionContent("재즈", "기존 질문", "기존 A", "기존 B"))
                        : Optional.empty());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(21L)));

        service.prefetchQuestions(TARGET_DATE);

        verify(aiVoteReadRepository, never()).findAllUserGenres();
        verify(prefetchWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void keepsSuccessfulDataWhenTheOtherAiViewFails() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(snapshotStore.findAssignments(TARGET_DATE)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(22L)));
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenThrow(new IllegalStateException("question view unavailable"));
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of());

        service.prefetchQuestions(TARGET_DATE);

        verify(prefetchWriter).save(TARGET_DATE, List.of(),
                List.of(new VoteSnapshotStore.AssignmentRow(22L, "공통", null)));
    }

    private User user(Long id) {
        User user = User.signup("u" + id + "@example.com", "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private AiVoteQuestion question(LocalDate date, String genre, String question) {
        return new AiVoteQuestion(date, genre, question, "선택 A", "선택 B");
    }

    private List<String> allGenres() {
        return java.util.stream.Stream.concat(
                java.util.Arrays.stream(PreferredGenre.values()).map(PreferredGenre::value),
                java.util.stream.Stream.of("공통")).toList();
    }

    private List<AiVoteQuestion> allQuestions() {
        return allGenres().stream()
                .map(genre -> question(TARGET_DATE, genre, genre + " 질문"))
                .toList();
    }
}
