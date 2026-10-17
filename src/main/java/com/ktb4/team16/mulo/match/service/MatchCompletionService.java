package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.Match;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.repository.MatchRepository;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchCompletionService {

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final Clock clock;

    /** 채팅방 확정과 참가자 두 명의 MATCHED 전이를 하나의 새 트랜잭션으로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long matchId, Long chatRoomId) {
        Match match = matchRepository.findByMatchIdForUpdate(matchId)
                .orElseThrow(() -> new IllegalStateException("매칭을 찾을 수 없습니다."));
        List<MatchRequest> requests = lockedPair(matchId);
        if (requests.stream().anyMatch(request -> request.getStatus() == MatchRequestStatus.FAILED)) {
            throw new IllegalStateException("실패 처리된 매칭은 완료할 수 없습니다.");
        }
        match.completeChatRoom(chatRoomId, LocalDateTime.now(clock));
        requests.forEach(MatchRequest::completeMatching);
    }

    /** 최종 실패는 완료된 매칭을 절대 덮어쓰지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long matchId) {
        Match match = matchRepository.findByMatchIdForUpdate(matchId)
                .orElseThrow(() -> new IllegalStateException("매칭을 찾을 수 없습니다."));
        if (match.getChatRoomId() != null) {
            return;
        }
        lockedPair(matchId).forEach(MatchRequest::failMatching);
    }

    private List<MatchRequest> lockedPair(Long matchId) {
        List<MatchRequest> requests = matchRequestRepository.findByMatchIdForUpdate(matchId);
        if (requests.size() != 2) {
            throw new IllegalStateException("매칭에는 정확히 두 개의 요청이 필요합니다.");
        }
        return requests;
    }
}
