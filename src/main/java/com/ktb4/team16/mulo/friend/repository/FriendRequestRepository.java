package com.ktb4.team16.mulo.friend.repository;

import com.ktb4.team16.mulo.friend.entity.FriendRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

/** pending 요청을 방향과 무관한 사용자 쌍으로 조회한다. */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /** 요청 방향을 보존한 단일 pending 요청을 조회한다. */
    Optional<FriendRequest> findByRequester_UserIdAndAddressee_UserId(
            Long requesterId,
            Long addresseeId
    );

    /** 생성·수락 경로에서 사용자 쌍의 pending 상태를 확인한다. */
    Optional<FriendRequest> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    /** 사용자 쌍의 pending 요청 존재 여부를 확인한다. */
    boolean existsByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    /** 사용자 쌍의 최신 pending 요청을 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM FriendRequest request
            WHERE request.userLowId = :userLowId
                AND request.userHighId = :userHighId
            """)
    Optional<FriendRequest> findByUserPairForUpdate(
            @Param("userLowId") Long userLowId,
            @Param("userHighId") Long userHighId
    );

    /** ID로 다시 읽기 전에 쌍 잠금을 획득한 요청 행을 최신 상태로 잠금 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM FriendRequest request WHERE request.friendRequestId = :requestId")
    Optional<FriendRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    /** 생성 시각·요청 ID 내림차순으로 받은 pending 첫 페이지를 조회한다. */
    @Query(value = """
            SELECT request.friend_request_id AS friendRequestId,
                other.user_id AS userId,
                other.nickname AS nickname,
                request.created_at AS createdAt
            FROM friend_requests request
            JOIN users other ON other.user_id = request.requester_id
            WHERE request.addressee_id = :currentUserId
                AND other.deleted_at IS NULL
            ORDER BY request.created_at DESC, request.friend_request_id DESC
            """, nativeQuery = true)
    List<FriendRequestListRow> findReceivedRequestsFirstPage(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );

    /** createdAt/friendRequestId 내림차순 경계 뒤의 받은 요청을 조회한다. */
    @Query(value = """
            SELECT request.friend_request_id AS friendRequestId,
                other.user_id AS userId,
                other.nickname AS nickname,
                request.created_at AS createdAt
            FROM friend_requests request
            JOIN users other ON other.user_id = request.requester_id
            WHERE request.addressee_id = :currentUserId
                AND other.deleted_at IS NULL
                AND (request.created_at < :afterCreatedAt
                    OR (request.created_at = :afterCreatedAt
                        AND request.friend_request_id < :afterFriendRequestId))
            ORDER BY request.created_at DESC, request.friend_request_id DESC
            """, nativeQuery = true)
    List<FriendRequestListRow> findReceivedRequestsAfterCursor(
            @Param("currentUserId") Long currentUserId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterFriendRequestId") Long afterFriendRequestId,
            Pageable pageable
    );

    /** 생성 시각·요청 ID 내림차순으로 보낸 pending 첫 페이지를 조회한다. */
    @Query(value = """
            SELECT request.friend_request_id AS friendRequestId,
                other.user_id AS userId,
                other.nickname AS nickname,
                request.created_at AS createdAt
            FROM friend_requests request
            JOIN users other ON other.user_id = request.addressee_id
            WHERE request.requester_id = :currentUserId
                AND other.deleted_at IS NULL
            ORDER BY request.created_at DESC, request.friend_request_id DESC
            """, nativeQuery = true)
    List<FriendRequestListRow> findSentRequestsFirstPage(
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
    );

    /** createdAt/friendRequestId 내림차순 경계 뒤의 보낸 요청을 조회한다. */
    @Query(value = """
            SELECT request.friend_request_id AS friendRequestId,
                other.user_id AS userId,
                other.nickname AS nickname,
                request.created_at AS createdAt
            FROM friend_requests request
            JOIN users other ON other.user_id = request.addressee_id
            WHERE request.requester_id = :currentUserId
                AND other.deleted_at IS NULL
                AND (request.created_at < :afterCreatedAt
                    OR (request.created_at = :afterCreatedAt
                        AND request.friend_request_id < :afterFriendRequestId))
            ORDER BY request.created_at DESC, request.friend_request_id DESC
            """, nativeQuery = true)
    List<FriendRequestListRow> findSentRequestsAfterCursor(
            @Param("currentUserId") Long currentUserId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterFriendRequestId") Long afterFriendRequestId,
            Pageable pageable
    );
}
