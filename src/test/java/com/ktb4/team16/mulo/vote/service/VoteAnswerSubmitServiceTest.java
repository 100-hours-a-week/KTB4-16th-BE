package com.ktb4.team16.mulo.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.dto.request.VoteAnswerSubmitRequest;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.exception.VoteDomainException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VoteAnswerSubmitServiceTest {

    @Mock TodayVoteQuestionService todayVoteQuestionService;
    @Mock UserRepository userRepository;
    @Mock VoteAnswerRepository voteAnswerRepository;
    @Mock VoteQuestionRepository voteQuestionRepository;
    @Mock User user;
    @Mock VoteQuestion question;

    private VoteAnswerSubmitService service;

    @BeforeEach
    void setUp() {
        service = new VoteAnswerSubmitService(todayVoteQuestionService, userRepository,
                voteAnswerRepository, voteQuestionRepository);
        lenient().when(todayVoteQuestionService.resolveTodayQuestion(17L)).thenReturn(question);
        lenient().when(question.getVoteQuestionId()).thenReturn(82L);
        lenient().when(question.getOptionA()).thenReturn("선택 A");
        lenient().when(question.getOptionB()).thenReturn("선택 B");
        lenient().when(voteQuestionRepository.findById(82L)).thenReturn(java.util.Optional.of(question));
        lenient().when(userRepository.findByUserIdAndDeletedAtIsNull(17L)).thenReturn(java.util.Optional.of(user));
        lenient().when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(82L, 17L))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void submitsOptionAAndReturnsCurrentRoundedPercentages() {
        when(voteAnswerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Object answer = invocation.getArgument(0);
            ReflectionTestUtils.setField(answer, "voteAnswerId", 43L);
            return answer;
        });
        VoteAnswerRepository.VoteAnswerCounts counts = counts(5L, 3L);
        when(voteAnswerRepository.aggregateForQuestion(82L, "선택 A")).thenReturn(counts);

        var response = service.submit(17L, 82L, new VoteAnswerSubmitRequest("A"));

        assertThat(response.message()).isEqualTo("투표 답변 성공");
        assertThat(response.data().voteAnswerId()).isEqualTo(43L);
        assertThat(response.data().myAnswer()).isEqualTo("선택 A");
        assertThat(response.data().result().optionAPercent()).isEqualTo(60);
        assertThat(response.data().result().optionBPercent()).isEqualTo(40);
    }

    @Test
    void submitsOptionBAndReturnsComplementaryPercentages() {
        when(voteAnswerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Object answer = invocation.getArgument(0);
            ReflectionTestUtils.setField(answer, "voteAnswerId", 44L);
            return answer;
        });
        VoteAnswerRepository.VoteAnswerCounts counts = counts(4L, 1L);
        when(voteAnswerRepository.aggregateForQuestion(82L, "선택 A")).thenReturn(counts);

        var response = service.submit(17L, 82L, new VoteAnswerSubmitRequest("B"));

        assertThat(response.data().myAnswer()).isEqualTo("선택 B");
        assertThat(response.data().result().optionAPercent()).isEqualTo(25);
        assertThat(response.data().result().optionBPercent()).isEqualTo(75);
    }

    @Test
    void roundsOptionAPercentageAndDerivesOptionBFromOneHundred() {
        when(voteAnswerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Object answer = invocation.getArgument(0);
            ReflectionTestUtils.setField(answer, "voteAnswerId", 45L);
            return answer;
        });
        VoteAnswerRepository.VoteAnswerCounts counts = counts(3L, 2L);
        when(voteAnswerRepository.aggregateForQuestion(82L, "선택 A")).thenReturn(counts);

        var response = service.submit(17L, 82L, new VoteAnswerSubmitRequest("A"));

        assertThat(response.data().result().optionAPercent()).isEqualTo(67);
        assertThat(response.data().result().optionBPercent()).isEqualTo(33);
    }

    @Test
    void rejectsQuestionNotAssignedToUser() {
        when(voteQuestionRepository.findById(83L)).thenReturn(java.util.Optional.of(question));

        assertThatThrownBy(() -> service.submit(17L, 83L, new VoteAnswerSubmitRequest("A")))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_QUESTION_NOT_ASSIGNED);
        verify(voteAnswerRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsNotFoundForUnknownQuestionId() {
        when(voteQuestionRepository.findById(83L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.submit(17L, 83L, new VoteAnswerSubmitRequest("A")))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_NOT_FOUND);
    }

    @Test
    void rejectsDuplicateAnswerBeforeInsert() {
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(82L, 17L))
                .thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(
                        com.ktb4.team16.mulo.vote.entity.VoteAnswer.class)));

        assertThatThrownBy(() -> service.submit(17L, 82L, new VoteAnswerSubmitRequest("B")))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_ALREADY_ANSWERED);
        verify(voteAnswerRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentDatabaseUniqueViolationToDuplicateVote() {
        when(voteAnswerRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("Duplicate for uk_vote_answers_user_question"));

        assertThatThrownBy(() -> service.submit(17L, 82L, new VoteAnswerSubmitRequest("A")))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_ALREADY_ANSWERED);
    }

    @Test
    void rejectsQuestionWithIdenticalOptions() {
        when(question.getOptionB()).thenReturn("선택 A");

        assertThatThrownBy(() -> service.submit(17L, 82L, new VoteAnswerSubmitRequest("A")))
                .isInstanceOf(VoteDomainException.class)
                .extracting(exception -> ((VoteDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.VOTE_OPTIONS_IDENTICAL);
        verify(voteAnswerRepository, never()).saveAndFlush(any());
    }

    @Test
    void requestAcceptsOnlyAOrBAndRejectsMissingOrInvalidValues() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validator.validate(new VoteAnswerSubmitRequest("A"))).isEmpty();
            assertThat(validator.validate(new VoteAnswerSubmitRequest("B"))).isEmpty();
            assertThat(validator.validate(new VoteAnswerSubmitRequest(null))).hasSize(1);
            assertThat(validator.validate(new VoteAnswerSubmitRequest(""))).hasSize(1);
            assertThat(validator.validate(new VoteAnswerSubmitRequest("C"))).hasSize(1);
        }
    }

    private VoteAnswerRepository.VoteAnswerCounts counts(long total, long optionA) {
        VoteAnswerRepository.VoteAnswerCounts counts = org.mockito.Mockito.mock(
                VoteAnswerRepository.VoteAnswerCounts.class);
        when(counts.getTotalCount()).thenReturn(total);
        when(counts.getOptionACount()).thenReturn(optionA);
        return counts;
    }
}
