package com.ktb4.team16.mulo.vote.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailySnapshotWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoteDailySnapshotServiceTest {
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 17);

    @Mock AiVoteReadRepository aiRepository;
    @Mock UserRepository userRepository;
    @Mock VoteSnapshotStore snapshotStore;
    @Mock VoteDailySnapshotWriter snapshotWriter;

    private VoteDailySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new VoteDailySnapshotService(aiRepository, userRepository,
                snapshotStore, snapshotWriter,
                Clock.fixed(Instant.parse("2026-10-16T14:55:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void schedulesFinalizationAt2355KstForTomorrowWithoutMidnightSchedule() throws Exception {
        Scheduled scheduled = VoteDailySnapshotService.class
                .getMethod("finalizeTomorrowSnapshot").getAnnotation(Scheduled.class);
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(true);

        service.finalizeTomorrowSnapshot();

        org.assertj.core.api.Assertions.assertThat(scheduled.cron()).isEqualTo("0 55 23 * * *");
        org.assertj.core.api.Assertions.assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        verify(snapshotStore).isReady(TARGET_DATE);
        verify(aiRepository, never()).findAllUserGenres();
        verify(aiRepository, never()).findQuestions(TARGET_DATE, TARGET_DATE);
    }

    @Test
    void readsAiDataBeforeDelegatingAtomicFinalization() {
        User user = user(17L);
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        when(aiRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of(
                new AiVoteQuestion(TARGET_DATE, "재즈", "질문", "A", "B")));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));

        service.createSnapshot(TARGET_DATE);

        verify(snapshotWriter).finalizeSnapshot(TARGET_DATE,
                List.of(new AiVoteQuestion(TARGET_DATE, "재즈", "질문", "A", "B")),
                List.of(17L), Optional.of(Map.of(17L, "재즈")));
    }

    @Test
    void distinguishesSuccessfulEmptyGenreViewFromFailedGenreRead() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of());
        when(aiRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of());
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));

        service.createSnapshot(TARGET_DATE);

        verify(snapshotWriter).finalizeSnapshot(TARGET_DATE, List.of(), List.of(17L), Optional.of(Map.of()));
    }

    @Test
    void preservesSavedDataAndReportsGenreReadFailureAsUnavailable() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenThrow(new IllegalStateException("AI view unavailable"));
        when(aiRepository.findQuestions(TARGET_DATE, TARGET_DATE)).thenReturn(List.of(
                new AiVoteQuestion(TARGET_DATE, "공통", "저장할 질문", "A", "B")));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));

        service.createSnapshot(TARGET_DATE);

        verify(snapshotWriter).finalizeSnapshot(TARGET_DATE,
                List.of(new AiVoteQuestion(TARGET_DATE, "공통", "저장할 질문", "A", "B")),
                List.of(17L), Optional.empty());
    }

    @Test
    void continuesWithSavedGenresWhenQuestionReadFails() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(true);
        when(aiRepository.findAllUserGenres()).thenReturn(Map.of(17L, "재즈"));
        when(aiRepository.findQuestions(TARGET_DATE, TARGET_DATE))
                .thenThrow(new IllegalStateException("AI question view unavailable"));
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));

        service.createSnapshot(TARGET_DATE);

        verify(snapshotWriter).finalizeSnapshot(TARGET_DATE, List.of(), List.of(17L),
                Optional.of(Map.of(17L, "재즈")));
    }

    @Test
    void doesNotTreatUnavailableAiDatabaseAsSuccessfulEmptyGenreView() {
        when(snapshotStore.isReady(TARGET_DATE)).thenReturn(false);
        when(aiRepository.isAvailable()).thenReturn(false);
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user(17L)));

        service.createSnapshot(TARGET_DATE);

        verify(aiRepository, never()).findAllUserGenres();
        verify(aiRepository, never()).findQuestions(TARGET_DATE, TARGET_DATE);
        verify(snapshotWriter).finalizeSnapshot(TARGET_DATE, List.of(), List.of(17L), Optional.empty());
    }

    private User user(Long id) {
        User user = User.signup("u" + id + "@example.com", "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
