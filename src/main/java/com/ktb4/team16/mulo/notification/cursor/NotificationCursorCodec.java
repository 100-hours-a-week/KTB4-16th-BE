package com.ktb4.team16.mulo.notification.cursor;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 서버가 발급한 timestamp/ID 조합을 API cursor 문자열로 변환한다. */
@Component
public class NotificationCursorCodec {

    private static final String DELIMITER = "|";
    private static final String SPLIT_DELIMITER = "\\|";

    /** 생성 시각과 알림 ID를 Base64 cursor로 인코딩한다. */
    public String encode(LocalDateTime createdAt, Long notificationId) {
        if (createdAt == null || notificationId == null || notificationId <= 0) {
            throw new IllegalArgumentException("유효한 알림 cursor 값이 필요합니다.");
        }
        String value = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(createdAt)
                + DELIMITER + notificationId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 잘못된 형식이나 식별자가 있는 cursor를 공통 400 오류로 변환한다. */
    public NotificationCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidCursorException();
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split(SPLIT_DELIMITER, -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InvalidCursorException();
            }
            LocalDateTime createdAt = LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            long notificationId = Long.parseLong(parts[1]);
            if (notificationId <= 0) {
                throw new InvalidCursorException();
            }
            return new NotificationCursor(createdAt, notificationId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new InvalidCursorException(exception);
        }
    }
}
