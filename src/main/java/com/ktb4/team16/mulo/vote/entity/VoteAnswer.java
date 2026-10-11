package com.ktb4.team16.mulo.vote.entity;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vote_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_answer_id")
    private Long voteAnswerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_question_id", nullable = false)
    private VoteQuestion voteQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "selected_option", nullable = false, length = 100)
    private String selectedOption;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
