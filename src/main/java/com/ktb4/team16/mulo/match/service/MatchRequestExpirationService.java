package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchRequestExpirationService {

    private final VoteQuestionRepository voteQuestionRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final Clock clock;

    @Transactional
    public int expireTodayWaitingRequests() {
        LocalDate today = LocalDate.now(clock);
        int expiredCount = 0;
        var questionIds = voteQuestionRepository.findVoteQuestionIdsByVoteDate(today);

        for (Long questionId : questionIds) {
            if (voteQuestionRepository.findByVoteQuestionIdForUpdate(questionId).isEmpty()) {
                continue;
            }
            var waitingRequests = matchRequestRepository.findByVoteQuestionIdAndStatusForUpdate(
                    questionId, MatchRequestStatus.WAITING);
            for (MatchRequest request : waitingRequests) {
                request.expire();
                expiredCount++;
            }
        }
        return expiredCount;
    }
}
