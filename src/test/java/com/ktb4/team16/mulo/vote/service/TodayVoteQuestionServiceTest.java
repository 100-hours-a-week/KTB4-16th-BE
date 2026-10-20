package com.ktb4.team16.mulo.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.exception.VoteDomainException;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailyAssignment;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailyAssignmentWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TodayVoteQuestionServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock UserRepository userRepository;
    @Mock VoteSnapshotStore snapshotStore;
    @Mock VoteDailyAssignmentWriter assignmentWriter;
    @Mock AiVoteReadRepository aiVoteReadRepository;
    @Mock VoteQuestionRepository voteQuestionRepository;
    @Mock VoteAnswerRepository voteAnswerRepository;

    private TodayVoteQuestionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new TodayVoteQuestionService(userRepository, snapshotStore,
                assignmentWriter, aiVoteReadRepository, voteQuestionRepository, voteAnswerRepository,
                Clock.fixed(Instant.parse("2026-10-15T15:00:00Z"), ZoneId.of("Asia/Seoul")));
        user = User.signup("vote@example.com", "hash", "투표사용자");
        ReflectionTestUtils.setField(user, "userId", 17L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(17L)).thenReturn(Optional.of(user));
    }

    @Test
    void returnsSnapshotNotReadyEvenWhenUserHasNoPreference() {
        ReflectionTestUtils.setField(user, "preferredGenres", null);
        when(snapshotStore.isReady(TODAY)).thenReturn(false);

        assertThatThrownBy(() -> service.getToday(17L))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
    }

    @Test
    void usesTheNewKstDateImmediatelyAfterMidnightWhenSnapshotIsReady() {
        LocalDate newDay = TODAY.plusDays(1);
        service = new TodayVoteQuestionService(userRepository, snapshotStore,
                assignmentWriter, aiVoteReadRepository, voteQuestionRepository, voteAnswerRepository,
                Clock.fixed(Instant.parse("2026-10-16T15:00:01Z"), ZoneId.of("Asia/Seoul")));
        when(snapshotStore.isReady(newDay)).thenReturn(true);
        when(snapshotStore.findAssignment(newDay, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 97L)));
        VoteQuestion question = mock(VoteQuestion.class);
        when(question.getVoteQuestionId()).thenReturn(97L);
        when(question.getVoteDate()).thenReturn(newDay);
        when(question.getMusicGenre()).thenReturn("재즈");
        when(voteQuestionRepository.findById(97L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(97L, 17L))
                .thenReturn(Optional.empty());

        var response = service.getToday(17L);

        assertThat(response.data().voteQuestionId()).isEqualTo(97L);
        verify(snapshotStore).isReady(newDay);
        verify(snapshotStore, never()).isReady(TODAY);
    }

    @Test
    void doesNotExposeTomorrowQuestionBeforeKstMidnight() {
        service = new TodayVoteQuestionService(userRepository, snapshotStore,
                assignmentWriter, aiVoteReadRepository, voteQuestionRepository, voteAnswerRepository,
                Clock.fixed(Instant.parse("2026-10-16T14:59:59Z"), ZoneId.of("Asia/Seoul")));
        when(snapshotStore.isReady(TODAY)).thenReturn(false);

        assertThatThrownBy(() -> service.getToday(17L))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_SNAPSHOT_NOT_READY);

        verify(snapshotStore, never()).findAssignment(TODAY, 17L);
        verify(snapshotStore, never()).isReady(TODAY.plusDays(1));
    }

    @Test
    void returnsQuestionAndNullAnswerForExistingDailyAssignment() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 82L)));
        VoteQuestion question = question(82L);
        when(voteQuestionRepository.findById(82L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(82L, 17L))
                .thenReturn(Optional.empty());

        var response = service.getToday(17L);

        assertThat(response.data().voteQuestionId()).isEqualTo(82L);
        assertThat(response.data().question()).isEqualTo("오늘 질문");
        assertThat(response.data().myAnswer()).isNull();
        assertThat(response.data().voteAnswerId()).isNull();
        verify(aiVoteReadRepository, never()).findUserGenre(17L);
    }

    @Test
    void includesExistingAnswerTextAndId() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 83L)));
        VoteQuestion question = question(83L);
        when(voteQuestionRepository.findById(83L)).thenReturn(Optional.of(question));
        VoteAnswer answer = mock(VoteAnswer.class);
        when(answer.getSelectedOption()).thenReturn("선택 A");
        when(answer.getVoteAnswerId()).thenReturn(101L);
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(83L, 17L))
                .thenReturn(Optional.of(answer));

        var response = service.getToday(17L);

        assertThat(response.data().myAnswer()).isEqualTo("선택 A");
        assertThat(response.data().voteAnswerId()).isEqualTo(101L);
    }

    @Test
    void lateUserUsesAiGroupAndCreatesImmutableDailyAssignment() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L)).thenReturn(Optional.empty());
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findUserGenre(17L)).thenReturn(Optional.of("인디음악"));
        when(snapshotStore.findQuestionId(TODAY, "인디음악")).thenReturn(Optional.of(91L));
        when(assignmentWriter.assignIfMissing(TODAY, 17L, "인디음악", 91L))
                .thenReturn(new VoteDailyAssignment("인디음악", 91L));
        VoteQuestion question = question(91L, "인디음악");
        when(voteQuestionRepository.findById(91L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(91L, 17L))
                .thenReturn(Optional.empty());

        assertThat(service.getToday(17L).data().voteQuestionId()).isEqualTo(91L);
        verify(assignmentWriter).assignIfMissing(TODAY, 17L, "인디음악", 91L);
    }

    @Test
    void absentAiUserGenreUsesCommonGroupWithoutPreferenceOrRecordRequirement() {
        ReflectionTestUtils.setField(user, "preferredGenres", null);
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L)).thenReturn(Optional.empty());
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findUserGenre(17L)).thenReturn(Optional.empty());
        when(snapshotStore.findQuestionId(TODAY, "공통")).thenReturn(Optional.of(93L));
        when(assignmentWriter.assignIfMissing(TODAY, 17L, "공통", 93L))
                .thenReturn(new VoteDailyAssignment("공통", 93L));
        VoteQuestion question = question(93L, "공통");
        when(voteQuestionRepository.findById(93L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(93L, 17L))
                .thenReturn(Optional.empty());

        assertThat(service.getToday(17L).data().voteQuestionId()).isEqualTo(93L);
        verify(assignmentWriter).assignIfMissing(TODAY, 17L, "공통", 93L);
    }

    @Test
    void doesNotReplaceExistingDailyAssignmentWhenAiGenreChanges() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 96L)));
        VoteQuestion question = question(96L, "재즈");
        when(voteQuestionRepository.findById(96L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(96L, 17L))
                .thenReturn(Optional.empty());

        var response = service.getToday(17L);

        assertThat(response.data().voteQuestionId()).isEqualTo(96L);
        verify(aiVoteReadRepository, never()).findUserGenre(17L);
        verify(assignmentWriter, never()).assignIfMissing(TODAY, 17L, "재즈", 96L);
    }

    @Test
    void returnsNotFoundWhenSnapshotHasNoQuestionForAssignedGroup() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", null)));

        assertThatThrownBy(() -> service.getToday(17L))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.TODAY_VOTE_NOT_FOUND);
    }

    @Test
    void returnsSnapshotNotReadyWhenAssignmentPointsToAnotherDateOrGenre() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 94L)));
        VoteQuestion question = mock(VoteQuestion.class);
        when(question.getMusicGenre()).thenReturn("공통");
        when(question.getVoteDate()).thenReturn(TODAY);
        when(voteQuestionRepository.findById(94L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.getToday(17L))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
    }

    @Test
    void existingAssignmentDoesNotRequireAiDatabaseAvailability() {
        when(snapshotStore.isReady(TODAY)).thenReturn(true);
        when(snapshotStore.findAssignment(TODAY, 17L))
                .thenReturn(Optional.of(new VoteDailyAssignment("재즈", 95L)));
        VoteQuestion question = question(95L);
        when(voteQuestionRepository.findById(95L)).thenReturn(Optional.of(question));
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(95L, 17L))
                .thenReturn(Optional.empty());

        var response = service.getToday(17L);

        assertThat(response.data().voteQuestionId()).isEqualTo(95L);
        verify(aiVoteReadRepository, never()).isAvailable();
    }

    private VoteQuestion question(Long id) {
        return question(id, "재즈");
    }

    private VoteQuestion question(Long id, String genre) {
        VoteQuestion question = mock(VoteQuestion.class);
        when(question.getVoteQuestionId()).thenReturn(id);
        when(question.getMusicGenre()).thenReturn(genre);
        when(question.getVoteDate()).thenReturn(TODAY);
        when(question.getQuestionText()).thenReturn("오늘 질문");
        when(question.getOptionA()).thenReturn("선택 A");
        when(question.getOptionB()).thenReturn("선택 B");
        return question;
    }
}
