package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import com.ktb4.team16.mulo.match.entity.Match;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.exception.MatchRequestConstraintViolationTranslator;
import com.ktb4.team16.mulo.match.repository.MatchRepository;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final VoteAnswerRepository voteAnswerRepository;
    private final VoteQuestionRepository voteQuestionRepository;
    private final Clock clock;

    @Transactional
    public MatchRequestCreateResponse createMatchRequest(
            Long userId,
            MatchRequestCreateRequest request
    ) {
        if (!LocalTime.now(clock).isBefore(LocalTime.of(23, 0))) {
            throw new MatchDomainException(ErrorCode.MATCH_REQUEST_CLOSED);
        }

        VoteAnswer voteAnswer = voteAnswerRepository.findById(request.voteAnswerId())
                .orElseThrow(() -> new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_FOUND));

        if (!voteAnswer.getUser().getUserId().equals(userId)) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_OWNED);
        }

        if (matchRequestRepository.existsByVoteAnswer_VoteAnswerId(voteAnswer.getVoteAnswerId())) {
            throw new MatchDomainException(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS);
        }

        VoteQuestion voteQuestion = voteQuestionRepository.findByVoteQuestionIdForUpdate(
                        voteAnswer.getVoteQuestion().getVoteQuestionId())
                .orElseThrow(() -> new IllegalStateException("투표 답변에 연결된 질문이 없습니다."));
        if (!LocalTime.now(clock).isBefore(LocalTime.of(23, 0))) {
            throw new MatchDomainException(ErrorCode.MATCH_REQUEST_CLOSED);
        }
        LocalDate today = LocalDate.now(clock);
        if (!voteQuestion.getVoteDate().equals(today)) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
        }

        MatchRequest requester = MatchRequest.waiting(voteAnswer.getUser(), voteAnswer);

        try {
            matchRequestRepository.saveAndFlush(requester);
        } catch (DataIntegrityViolationException exception) {
            throw MatchRequestConstraintViolationTranslator.translate(exception);
        }

        List<MatchRequest> candidates = matchRequestRepository.findOppositeWaitingForUpdate(
                voteAnswer.getVoteAnswerId(), PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return new MatchRequestCreateResponse(
                    "매칭 요청 생성 성공", // 매칭이 성공한 것은 아님
                    new MatchRequestCreateResponse.Data(
                            requester.getMatchRequestId(), requester.getStatus()));
        }

        Match match = matchRepository.save(Match.create(voteQuestion));
        requester.startMatching(match);
        candidates.getFirst().startMatching(match);

        return new MatchRequestCreateResponse(
                "매칭 요청 생성 성공",
                new MatchRequestCreateResponse.Data(requester.getMatchRequestId(), requester.getStatus()));

    }
}
