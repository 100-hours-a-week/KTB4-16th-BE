package com.ktb4.team16.mulo.match.entity;

import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
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
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_question_id", nullable = false)
    private VoteQuestion voteQuestion;

    // 채팅 서버가 소유하는 논리 식별자이며 백엔드 데이터베이스 외래 키로 연결하지 않는다.
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    // 생성자
    private Match(VoteQuestion voteQuestion) {
        if (voteQuestion == null) {
            throw new IllegalArgumentException("투표 질문이 필요합니다.");
        }
        this.voteQuestion = voteQuestion;
    }

    public static Match create(VoteQuestion voteQuestion) {
        return new Match(voteQuestion);
    }
}
