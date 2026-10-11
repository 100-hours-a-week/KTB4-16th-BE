package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteQuestionRepository extends JpaRepository<VoteQuestion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT question FROM VoteQuestion question WHERE question.voteQuestionId = :voteQuestionId")
    Optional<VoteQuestion> findByVoteQuestionIdForUpdate(@Param("voteQuestionId") Long voteQuestionId);
}
