package com.ktb4.team16.mulo.notification.controller;

import com.ktb4.team16.mulo.notification.dto.NotificationActionResponse;
import com.ktb4.team16.mulo.notification.dto.NotificationListResponse;
import com.ktb4.team16.mulo.notification.dto.NotificationReadRequest;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 현재 사용자의 알림 조회·읽음·삭제 HTTP 요청을 서비스로 전달한다. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 인증 사용자의 알림 첫 페이지 또는 cursor 이후 페이지를 반환한다. */
    @GetMapping
    public NotificationListResponse getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return notificationService.getNotifications(userId, cursor, size);
    }

    /** 요청한 알림 ID 전체를 검증한 뒤 일괄 읽음 처리한다. */
    @PatchMapping("/read")
    public NotificationActionResponse markRead(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NotificationReadRequest request
    ) {
        notificationService.markRead(userId, request.notificationIds());
        return new NotificationActionResponse("알림을 읽음 처리했습니다.");
    }

    /** 현재 사용자가 소유한 알림만 삭제한다. */
    @DeleteMapping("/{notificationId}")
    public NotificationActionResponse deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.delete(userId, notificationId);
        return new NotificationActionResponse("알림이 삭제되었습니다.");
    }
}
