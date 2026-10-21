package com.ktb4.team16.mulo.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.vote.dto.response.VoteAnswerSubmitResponse;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteAnswerResultServiceTest {
    @Mock VoteAnswerRepository voteAnswerRepository;

    private VoteAnswerResultService service;
    private VoteQuestion question;

    @BeforeEach
    void setUp() {
        service = new VoteAnswerResultService(voteAnswerRepository);
        question = mock(VoteQuestion.class);
        when(question.getVoteQuestionId()).thenReturn(42L);
        when(question.getOptionA()).thenReturn("A 선택지");
    }

    @Test
    void calculatesA60B40ForQuestionIdOnly() {
        whenCounts(5L, 3L);

        assertThat(service.getResult(question))
                .isEqualTo(new VoteAnswerSubmitResponse.Result(60, 40));

        verify(voteAnswerRepository).aggregateForQuestion(42L, "A 선택지");
    }

    @Test
    void calculatesEvenSplit() {
        whenCounts(4L, 2L);

        assertThat(service.getResult(question))
                .isEqualTo(new VoteAnswerSubmitResponse.Result(50, 50));
    }

    @Test
    void calculatesAllVotesForOptionA() {
        whenCounts(3L, 3L);

        assertThat(service.getResult(question))
                .isEqualTo(new VoteAnswerSubmitResponse.Result(100, 0));
    }

    @Test
    void calculatesAllVotesForOptionB() {
        whenCounts(3L, 0L);

        assertThat(service.getResult(question))
                .isEqualTo(new VoteAnswerSubmitResponse.Result(0, 100));
    }

    @Test
    void returnsZeroPercentagesWhenThereAreNoAnswers() {
        whenCounts(0L, 0L);

        assertThat(service.getResult(question))
                .isEqualTo(new VoteAnswerSubmitResponse.Result(0, 0));
    }

    private void whenCounts(long total, long optionA) {
        VoteAnswerRepository.VoteAnswerCounts counts = mock(VoteAnswerRepository.VoteAnswerCounts.class);
        when(counts.getTotalCount()).thenReturn(total);
        when(counts.getOptionACount()).thenReturn(optionA);
        when(voteAnswerRepository.aggregateForQuestion(42L, "A 선택지")).thenReturn(counts);
    }
}
