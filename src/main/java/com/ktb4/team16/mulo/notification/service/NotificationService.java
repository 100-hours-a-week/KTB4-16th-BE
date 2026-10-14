package com.ktb4.team16.mulo.notification.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.notification.cursor.NotificationCursor;
import com.ktb4.team16.mulo.notification.cursor.NotificationCursorCodec;
import com.ktb4.team16.mulo.notification.dto.NotificationListResponse;
import com.ktb4.team16.mulo.notification.entity.Notification;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import com.ktb4.team16.mulo.notification.exception.NotificationInputException;
import com.ktb4.team16.mulo.notification.repository.NotificationRepository;
import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 사용자에게 필요한 알림 조회와 소유권이 검증된 상태 변경을 제공한다. */
@Service
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;
    private final NotificationCursorCodec cursorCodec;

    /** 알림 저장소와 서버 시계 및 cursor 변환기를 연결한다. */
    public NotificationService(NotificationRepository notifications, Clock clock,
            NotificationCursorCodec cursorCodec) {
        this.notifications = notifications;
        this.clock = clock;
        this.cursorCodec = cursorCodec;
    }

    /** 현재 사용자 알림을 최신순 keyset 페이지로 조회하며 read_at은 변경하지 않는다. */
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, String cursor, Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new NotificationInputException("size", ErrorCode.INVALID_SIZE);
        }

        NotificationCursor boundary = decodeCursor(cursor);
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<Notification> found = boundary == null
                ? notifications.findFirstPage(userId, pageRequest)
                : notifications.findAfterCursor(userId, boundary.createdAt(),
                        boundary.notificationId(), pageRequest);
        boolean hasNext = found.size() > size;
        List<Notification> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? cursorCodec.encode(page.getLast().getCreatedAt(), page.getLast().getNotificationId())
                : null;
        List<NotificationListResponse.Item> items = page.stream()
                .map(NotificationListResponse.Item::from)
                .toList();
        return new NotificationListResponse("알림 목록 조회 성공",
                new NotificationListResponse.Data(items, nextCursor));
    }

    /** 요청 ID의 전체 소유권을 먼저 확인하고 unread 알림만 같은 시각으로 갱신한다. */
    @Transactional
    public void markRead(Long userId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new NotificationDomainException(ErrorCode.NOTIFICATION_IDS_REQUIRED);
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new NotificationInputException("notificationIds", ErrorCode.INVALID_NOTIFICATION_ID);
        }
        if (notifications.countOwnedNotifications(ids, userId) != ids.size()) {
            throw new NotificationDomainException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notifications.markUnreadAsRead(ids, userId, LocalDateTime.now(clock));
    }

    /** 현재 사용자가 소유한 알림 한 건만 삭제한다. */
    @Transactional
    public void delete(Long userId, Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new NotificationInputException("notificationId", ErrorCode.INVALID_NOTIFICATION_ID);
        }
        if (notifications.deleteByNotificationIdAndUser_UserId(notificationId, userId) == 0) {
            throw new NotificationDomainException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    /** malformed cursor를 기존 API의 cursor 필드 오류로 변환한다. */
    private NotificationCursor decodeCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            return cursorCodec.decode(cursor);
        } catch (InvalidCursorException exception) {
            throw new NotificationInputException("cursor", ErrorCode.INVALID_CURSOR);
        }
    }
}
