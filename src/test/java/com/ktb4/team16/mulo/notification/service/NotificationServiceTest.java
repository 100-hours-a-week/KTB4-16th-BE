package com.ktb4.team16.mulo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import com.ktb4.team16.mulo.notification.cursor.NotificationCursorCodec;
import com.ktb4.team16.mulo.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationRepository notifications;

    /** 현재 사용자가 요청한 모든 ID를 소유할 때만 하나의 시각으로 일괄 읽음 처리한다. */
    @Test
    void marksAllOwnedNotificationsReadAtOneTimestamp() {
        NotificationService service = new NotificationService(notifications, FIXED_CLOCK,
                new NotificationCursorCodec());
        when(notifications.countOwnedNotifications(List.of(101L, 102L), 7L)).thenReturn(2L);

        service.markRead(7L, List.of(101L, 102L));

        verify(notifications).markUnreadAsRead(List.of(101L, 102L), 7L,
                LocalDateTime.of(2026, 10, 11, 0, 0));
    }

    /** 타인 소유 ID가 한 건이라도 섞이면 일부 읽음 변경 없이 404로 끝낸다. */
    @Test
    void rejectsMixedOwnershipBeforeChangingAnyReadState() {
        NotificationService service = new NotificationService(notifications, FIXED_CLOCK,
                new NotificationCursorCodec());
        when(notifications.countOwnedNotifications(List.of(101L, 999L), 7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.markRead(7L, List.of(101L, 999L)))
                .isInstanceOf(NotificationDomainException.class)
                .satisfies(exception -> assertThat(((NotificationDomainException) exception)
                        .errorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));

        verify(notifications, never()).markUnreadAsRead(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 빈 알림 ID 배열을 서비스 경계에서도 거부해 비HTTP 호출의 안전성도 보장한다. */
    @Test
    void rejectsEmptyReadRequest() {
        NotificationService service = new NotificationService(notifications, FIXED_CLOCK,
                new NotificationCursorCodec());

        assertThatThrownBy(() -> service.markRead(7L, List.of()))
                .isInstanceOf(NotificationDomainException.class)
                .satisfies(exception -> assertThat(((NotificationDomainException) exception)
                        .errorCode()).isEqualTo(ErrorCode.NOTIFICATION_IDS_REQUIRED));
    }
}
