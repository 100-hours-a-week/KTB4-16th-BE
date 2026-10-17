package com.ktb4.team16.mulo.match.repository;

import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteAnswerRepository extends JpaRepository<VoteAnswer, Long> {
    Optional<VoteAnswer> findByVoteQuestion_VoteQuestionIdAndUser_UserId(
            Long voteQuestionId, Long userId);
}
