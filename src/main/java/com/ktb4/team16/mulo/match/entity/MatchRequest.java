package com.ktb4.team16.mulo.match.entity;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "match_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_request_id")
    private Long matchRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_answer_id", nullable = false)
    private VoteAnswer voteAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private MatchRequest(User user, VoteAnswer voteAnswer) {
        if (user == null || voteAnswer == null) {
            throw new IllegalArgumentException("사용자와 투표 답변이 필요합니다.");
        }
        this.user = user;
        this.voteAnswer = voteAnswer;
        this.status = MatchRequestStatus.WAITING;
    }

    public static MatchRequest waiting(User user, VoteAnswer voteAnswer) {
        return new MatchRequest(user, voteAnswer);
    }

    public void startMatching(Match match) {
        if (this.status != MatchRequestStatus.WAITING || match == null) {
            throw new IllegalStateException("대기 중인 요청만 매칭할 수 있습니다.");
        }
        this.match = match;
        this.status = MatchRequestStatus.MATCHING;
    }
}
