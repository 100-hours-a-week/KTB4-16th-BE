package com.ktb4.team16.mulo.notification.controller;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.notification.dto.NotificationActionResponse;
import com.ktb4.team16.mulo.notification.dto.NotificationListResponse;
import com.ktb4.team16.mulo.notification.dto.NotificationReadRequest;
import com.ktb4.team16.mulo.notification.dto.NotificationUnreadStatusResponse;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 현재 사용자의 알림 조회·읽음·삭제 HTTP 요청을 서비스로 전달한다. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseConnectionManager sseConnections;
    private final JwtTokenProvider jwtTokenProvider;

    /** 인증 사용자의 알림 첫 페이지 또는 cursor 이후 페이지를 반환한다. */
    @GetMapping
    public NotificationListResponse getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return notificationService.getNotifications(userId, cursor, size);
    }

    @GetMapping("/unread-status")
    public NotificationUnreadStatusResponse getUnreadStatus(@AuthenticationPrincipal Long userId) {
        return notificationService.getUnreadStatus(userId);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Long userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletResponse response) {
        if (userId == null || authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthenticatedUserException();
        }
        try {
            var token = jwtTokenProvider.extractAccessTokenDetails(authorization.substring(7));
            if (!userId.equals(token.userId())) {
                throw new UnauthenticatedUserException();
            }
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            return sseConnections.connect(userId, token.expiresAt(), token.sessionId());
        } catch (JwtException exception) {
            throw new UnauthenticatedUserException();
        }
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
