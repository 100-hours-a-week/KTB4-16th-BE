package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteAnswerRepository extends JpaRepository<VoteAnswer, Long> {
    Optional<VoteAnswer> findByVoteQuestion_VoteQuestionIdAndUser_UserId(
            Long voteQuestionId, Long userId);

    @Query(value = "SELECT COUNT(*) AS totalCount, "
            + "COUNT(CASE WHEN selected_option = :optionA THEN 1 END) AS optionACount "
            + "FROM vote_answers WHERE vote_question_id = :voteQuestionId", nativeQuery = true)
    VoteAnswerCounts aggregateForQuestion(@Param("voteQuestionId") Long voteQuestionId,
            @Param("optionA") String optionA);

    interface VoteAnswerCounts {
        Long getTotalCount();

        Long getOptionACount();
    }
}
