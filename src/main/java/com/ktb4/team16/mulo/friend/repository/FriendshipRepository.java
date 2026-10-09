package com.ktb4.team16.mulo.friend.repository;

import com.ktb4.team16.mulo.friend.entity.Friendship;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

/** 무방향 친구 관계를 정규화된 사용자 쌍으로 조회한다. */
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** 두 사용자의 유일한 친구 관계를 조회한다. */
    Optional<Friendship> findByUserLow_UserIdAndUserHigh_UserId(Long userLowId, Long userHighId);

    /** 친구 요청 생성 전에 이미 관계가 있는지 확인한다. */
    boolean existsByUserLow_UserIdAndUserHigh_UserId(Long userLowId, Long userHighId);

    /** 사용자 쌍의 최신 친구 관계를 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT friendship
            FROM Friendship friendship
            WHERE friendship.userLow.userId = :userLowId
                AND friendship.userHigh.userId = :userHighId
            """)
    Optional<Friendship> findByUserPairForUpdate(
            @Param("userLowId") Long userLowId,
            @Param("userHighId") Long userHighId
    );

    /** ID로 다시 읽기 전에 쌍 잠금을 획득한 관계 행을 최신 상태로 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT friendship FROM Friendship friendship WHERE friendship.friendshipId = :friendshipId")
    Optional<Friendship> findByIdForUpdate(@Param("friendshipId") Long friendshipId);

    /** 닉네임·사용자 ID 순서의 친구 첫 페이지와 다음 항목을 가져온다. */
    @Query(value = """
            SELECT friendship.friendship_id AS friendshipId,
                other.user_id AS userId,
                other.nickname AS nickname
            FROM friendships friendship
            JOIN users other
                ON other.user_id = CASE
                    WHEN friendship.user_low_id = :currentUserId
                        THEN friendship.user_high_id
                    ELSE friendship.user_low_id
                END
            WHERE (friendship.user_low_id = :currentUserId
                    OR friendship.user_high_id = :currentUserId)
                AND other.deleted_at IS NULL
            ORDER BY other.nickname ASC, other.user_id ASC
            """, nativeQuery = true)
    List<FriendListRow> findFriendsFirstPage(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );

    /** nickname/userId keyset 경계 뒤에 있는 친구를 같은 DB 정렬식으로 조회한다. */
    @Query(value = """
            SELECT friendship.friendship_id AS friendshipId,
                other.user_id AS userId,
                other.nickname AS nickname
            FROM friendships friendship
            JOIN users other
                ON other.user_id = CASE
                    WHEN friendship.user_low_id = :currentUserId
                        THEN friendship.user_high_id
                    ELSE friendship.user_low_id
                END
            WHERE (friendship.user_low_id = :currentUserId
                    OR friendship.user_high_id = :currentUserId)
                AND other.deleted_at IS NULL
                AND (other.nickname > :afterNickname
                    OR (other.nickname = :afterNickname AND other.user_id > :afterUserId))
            ORDER BY other.nickname ASC, other.user_id ASC
            """, nativeQuery = true)
    List<FriendListRow> findFriendsAfterCursor(
            @Param("currentUserId") Long currentUserId,
            @Param("afterNickname") String afterNickname,
            @Param("afterUserId") Long afterUserId,
            Pageable pageable
    );
}
