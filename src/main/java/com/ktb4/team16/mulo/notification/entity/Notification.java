package com.ktb4.team16.mulo.notification.entity;

import com.ktb4.team16.mulo.friend.entity.FriendRequest;
import com.ktb4.team16.mulo.friend.entity.Friendship;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.user.entity.User;
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

/** V7 notifications 테이블의 사용자별 서비스 알림 이력을 매핑한다. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_report_id")
    private MonthlyReport monthlyReport;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friendship_id")
    private Friendship friendship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_request_id")
    private FriendRequest friendRequest;

    @Column(name = "source_event_id", length = 36)
    private String sourceEventId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Target FK가 삭제로 NULL이 되어도 유형에 맞는 당시 target ID를 반환한다. */
    public Long targetId() {
        return switch (type) {
            case MONTHLY_REPORT_CREATED -> monthlyReport == null ? null : monthlyReport.getMonthlyReportId();
            case MATCH_COMPLETED -> chatRoomId;
            case FRIEND_REQUEST -> friendRequest == null ? null : friendRequest.getFriendRequestId();
            case FRIEND_ACCEPTED -> friendship == null ? null : friendship.getFriendshipId();
        };
    }

    /** 알림 유형을 OpenAPI의 targetType 문자열로 변환한다. */
    public String targetType() {
        return switch (type) {
            case MONTHLY_REPORT_CREATED -> "MONTHLY_REPORT";
            case MATCH_COMPLETED -> "CHAT_ROOM";
            case FRIEND_REQUEST -> "FRIEND_REQUEST";
            case FRIEND_ACCEPTED -> "FRIENDSHIP";
        };
    }
}
