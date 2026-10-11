package com.ktb4.team16.mulo.notification.repository;

import com.ktb4.team16.mulo.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사용자의 알림 소유권과 최신순 keyset 조회를 데이터베이스에서 적용한다. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 생성 시각·ID 내림차순으로 알림 첫 페이지와 다음 행을 조회한다. */
    @Query("""
            SELECT notification FROM Notification notification
            WHERE notification.user.userId = :userId
            ORDER BY notification.createdAt DESC, notification.notificationId DESC
            """)
    List<Notification> findFirstPage(@Param("userId") Long userId, Pageable pageable);

    /** 두 필드가 같은 시각의 알림을 중복 없이 이어 받도록 keyset 경계 뒤를 조회한다. */
    @Query("""
            SELECT notification FROM Notification notification
            WHERE notification.user.userId = :userId
                AND (notification.createdAt < :afterCreatedAt
                    OR (notification.createdAt = :afterCreatedAt
                        AND notification.notificationId < :afterNotificationId))
            ORDER BY notification.createdAt DESC, notification.notificationId DESC
            """)
    List<Notification> findAfterCursor(
            @Param("userId") Long userId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterNotificationId") Long afterNotificationId,
            Pageable pageable
    );

    /** ID 집합 중 현재 사용자가 소유한 알림 수를 원자적 검증 전에 계산한다. */
    @Query("""
            SELECT COUNT(notification) FROM Notification notification
            WHERE notification.user.userId = :userId
                AND notification.notificationId IN :notificationIds
            """)
    long countOwnedNotifications(
            @Param("notificationIds") List<Long> notificationIds,
            @Param("userId") Long userId
    );

    /** 검증된 소유자의 unread 행만 하나의 시각으로 읽음 처리한다. */
    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :readAt
            WHERE notification.user.userId = :userId
                AND notification.notificationId IN :notificationIds
                AND notification.readAt IS NULL
            """)
    int markUnreadAsRead(
            @Param("notificationIds") List<Long> notificationIds,
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt
    );

    /** 현재 사용자의 지정 알림 한 건만 삭제한다. */
    long deleteByNotificationIdAndUser_UserId(Long notificationId, Long userId);
}
