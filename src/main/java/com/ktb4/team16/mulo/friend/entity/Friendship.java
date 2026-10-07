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

/** 방향이 없는 한 사용자 쌍의 친구 관계를 매핑한다. */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "friendship_id")
    private Long friendshipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_low_id", nullable = false)
    private User userLow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_high_id", nullable = false)
    private User userHigh;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 정렬된 사용자 쌍을 친구 관계의 양 끝에 저장한다. */
    private Friendship(User lowUser, User highUser) {
        this.userLow = lowUser;
        this.userHigh = highUser;
    }

    /** 사용자 순서를 정규화한 무방향 친구 관계를 생성한다. */
    public static Friendship between(User firstUser, User secondUser) {
        if (firstUser == null || secondUser == null) {
            throw new IllegalArgumentException("두 사용자가 필요합니다.");
        }

        UserPair pair = UserPair.of(firstUser.getUserId(), secondUser.getUserId());
        return pair.lowId().equals(firstUser.getUserId())
                ? new Friendship(firstUser, secondUser)
                : new Friendship(secondUser, firstUser);
    }

    /** 현재 사용자가 이 친구 관계의 구성원인지 확인한다. */
    public boolean containsUser(Long userId) {
        return userId != null
                && (userLow.getUserId().equals(userId) || userHigh.getUserId().equals(userId));
    }
}
