package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestStatusResponse;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private final MatchRequestRepository matchRequestRepository;

    /** 기존 매칭 요청의 상태 조회에 필요한 저장소를 주입한다. */
    public MatchService(MatchRequestRepository matchRequestRepository) {
        this.matchRequestRepository = matchRequestRepository;
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
        throw new MatchDomainException(ErrorCode.ONE_TO_ONE_MATCHING_REPLACED);
    }
}
