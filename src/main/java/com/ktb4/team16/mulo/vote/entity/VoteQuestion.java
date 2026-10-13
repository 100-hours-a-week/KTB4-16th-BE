package com.ktb4.team16.mulo.vote.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vote_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_question_id")
    private Long voteQuestionId;

    @Column(name = "question_text", nullable = false, length = 100)
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 100)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 100)
    private String optionB;

    @Column(name = "music_genre", nullable = false, length = 50)
    private String musicGenre;

    @Column(name = "vote_date", nullable = false)
    private LocalDate voteDate;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
