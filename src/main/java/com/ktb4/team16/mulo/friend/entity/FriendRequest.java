package com.ktb4.team16.mulo.friend.entity;

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

/** 처리 전인 단일 방향 친구 요청을 매핑한다. */
@Entity
@Table(name = "friend_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "friend_request_id")
    private Long friendRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    // 중요: 두 generated column은 DB UNIQUE 제약의 기준이므로 애플리케이션에서 쓰지 않는다.
    @Column(name = "user_low_id", insertable = false, updatable = false)
    private Long userLowId;

    @Column(name = "user_high_id", insertable = false, updatable = false)
    private Long userHighId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 두 참여자의 ID를 검증하고 요청 방향을 보존한다. */
    private FriendRequest(User requester, User addressee) {
        UserPair.of(requester.getUserId(), addressee.getUserId());
        this.requester = requester;
        this.addressee = addressee;
    }

    /** 두 활성 사용자의 pending 요청 Entity를 생성한다. */
    public static FriendRequest pending(User requester, User addressee) {
        if (requester == null || addressee == null) {
            throw new IllegalArgumentException("요청자와 수신자가 필요합니다.");
        }
        return new FriendRequest(requester, addressee);
    }
}
