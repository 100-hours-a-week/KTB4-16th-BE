package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.groupchat.config.GroupChatProperties;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestStatusResponse;
import com.ktb4.team16.mulo.match.entity.Match;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.event.MatchCreatedEvent;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.exception.MatchRequestConstraintViolationTranslator;
import com.ktb4.team16.mulo.match.repository.MatchRepository;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final VoteAnswerRepository voteAnswerRepository;
    private final VoteQuestionRepository voteQuestionRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupChatProperties groupChatProperties;

    @Autowired
    public MatchService(
            MatchRepository matchRepository,
            MatchRequestRepository matchRequestRepository,
            VoteAnswerRepository voteAnswerRepository,
            VoteQuestionRepository voteQuestionRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            GroupChatProperties groupChatProperties
    ) {
        this.matchRepository = matchRepository;
        this.matchRequestRepository = matchRequestRepository;
        this.voteAnswerRepository = voteAnswerRepository;
        this.voteQuestionRepository = voteQuestionRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.groupChatProperties = groupChatProperties;
    }

    MatchService(MatchRepository matchRepository, MatchRequestRepository matchRequestRepository,
            VoteAnswerRepository voteAnswerRepository, VoteQuestionRepository voteQuestionRepository,
            Clock clock, ApplicationEventPublisher eventPublisher) {
        this(matchRepository, matchRequestRepository, voteAnswerRepository, voteQuestionRepository, clock,
                eventPublisher, new GroupChatProperties(false, LocalDate.of(2026, 10, 22)));
    }

    MatchService(
            MatchRepository matchRepository,
            MatchRequestRepository matchRequestRepository,
            VoteAnswerRepository voteAnswerRepository,
            VoteQuestionRepository voteQuestionRepository,
            Clock clock
    ) {
        this(matchRepository, matchRequestRepository, voteAnswerRepository, voteQuestionRepository, clock,
                event -> { });
    }

    @Transactional(readOnly = true)
    public MatchRequestStatusResponse getMatchRequestStatus(Long userId, Long matchRequestId) {
        MatchRequest matchRequest = matchRequestRepository
                .findByMatchRequestIdAndUser_UserId(matchRequestId, userId)
                .orElseThrow(() -> new MatchDomainException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        return toStatusResponse(matchRequest);
    }

    @Transactional(readOnly = true)
    public MatchRequestStatusResponse getMatchRequestStatusByVoteAnswer(Long userId, Long voteAnswerId) {
        MatchRequest matchRequest = matchRequestRepository.findByVoteAnswerIdAndUserId(voteAnswerId, userId)
                .orElseThrow(() -> new MatchDomainException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        return toStatusResponse(matchRequest);
    }

    private MatchRequestStatusResponse toStatusResponse(MatchRequest matchRequest) {
        if (matchRequest.getStatus() == MatchRequestStatus.MATCHED
                && (matchRequest.getMatch() == null || matchRequest.getMatch().getChatRoomId() == null)) {
            throw new IllegalStateException("MATCHED 매칭 요청에는 채팅방 식별자가 필요합니다.");
        }

        return MatchRequestStatusResponse.from("매칭 요청 상태 조회 성공", matchRequest);
    }

    @Transactional
    public MatchRequestCreateResponse createMatchRequest(
            Long userId,
            MatchRequestCreateRequest request
    ) {
        ensureOneToOneMatchingNotReplaced();
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
        ensureOneToOneMatchingNotReplaced();
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

        Match match = matchRepository.saveAndFlush(Match.create(voteQuestion));
        requester.startMatching(match);
        MatchRequest candidate = candidates.getFirst();
        candidate.startMatching(match);
        eventPublisher.publishEvent(new MatchCreatedEvent(match.getMatchId(), createChatRoomRequest(match, requester, candidate)));

        return new MatchRequestCreateResponse(
                "매칭 요청 생성 성공",
                new MatchRequestCreateResponse.Data(requester.getMatchRequestId(), requester.getStatus()));

    }

    private void ensureOneToOneMatchingNotReplaced() {
        if (groupChatProperties.isAvailableOn(LocalDate.now(clock))) {
            throw new MatchDomainException(ErrorCode.ONE_TO_ONE_MATCHING_REPLACED);
        }
    }

    private ChatRoomCreateRequest createChatRoomRequest(
            Match match, MatchRequest requester, MatchRequest candidate
    ) {
        Long requesterId = requester.getUser().getUserId();
        Long candidateId = candidate.getUser().getUserId();
        if (requesterId == null || candidateId == null || requesterId.equals(candidateId)) {
            throw new IllegalStateException("서로 다른 두 활성 사용자가 필요합니다.");
        }
        OffsetDateTime expiresAt = match.getVoteQuestion().getVoteDate()
                .plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
        return new ChatRoomCreateRequest(match.getMatchId(), expiresAt, List.of(
                new ChatRoomCreateRequest.Participant(requesterId, requester.getUser().getNickname()),
                new ChatRoomCreateRequest.Participant(candidateId, candidate.getUser().getNickname())));
    }
}
