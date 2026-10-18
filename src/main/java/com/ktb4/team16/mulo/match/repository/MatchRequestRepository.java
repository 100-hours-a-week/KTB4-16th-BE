package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    Optional<MatchRequest> findByMatchRequestIdAndUser_UserId(Long matchRequestId, Long userId);

    boolean existsByVoteAnswer_VoteAnswerId(Long voteAnswerId);

    // 선입선출 후보 하나에만 잠금이 적용되도록 호출 시 페이지 크기를 1로 제한한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT candidate
            FROM MatchRequest candidate
            JOIN candidate.user candidateUser
            JOIN candidate.voteAnswer candidateAnswer
            JOIN candidateAnswer.voteQuestion question
            JOIN VoteAnswer requesterAnswer ON requesterAnswer.voteQuestion = question
            WHERE requesterAnswer.voteAnswerId = :requesterVoteAnswerId
                AND candidate.status = com.ktb4.team16.mulo.match.domain.MatchRequestStatus.WAITING
                AND candidateUser.deletedAt IS NULL
                AND question.optionA <> question.optionB
                AND (
                    (requesterAnswer.selectedOption = question.optionA
                        AND candidateAnswer.selectedOption = question.optionB)
                    OR
                    (requesterAnswer.selectedOption = question.optionB
                        AND candidateAnswer.selectedOption = question.optionA)
                )
            ORDER BY candidate.createdAt ASC, candidate.matchRequestId ASC
            """)
    List<MatchRequest> findOppositeWaitingForUpdate(
            @Param("requesterVoteAnswerId") Long requesterVoteAnswerId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM MatchRequest request
            JOIN request.voteAnswer answer
            WHERE answer.voteQuestion.voteQuestionId = :voteQuestionId
                AND request.status = :status
            ORDER BY request.matchRequestId ASC
            """)
    List<MatchRequest> findByVoteQuestionIdAndStatusForUpdate(
            @Param("voteQuestionId") Long voteQuestionId,
            @Param("status") MatchRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM MatchRequest request
            WHERE request.match.matchId = :matchId
            ORDER BY request.matchRequestId ASC
            """)
    List<MatchRequest> findByMatchIdForUpdate(@Param("matchId") Long matchId);
}
