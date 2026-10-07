package com.ktb4.team16.mulo.friend.cursor;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 생성 시각·요청 ID 정렬 키를 친구 요청 목록 전용 opaque cursor로 변환한다. */
@Component
public final class RequestCursorCodec {

    private static final String PREFIX = "r1";
    private static final int MAX_CURSOR_LENGTH = 256;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** 서버가 반환할 다음 요청 페이지 경계 커서를 만든다. */
    public String encode(LocalDateTime createdAt, Long friendRequestId) {
        if (createdAt == null || friendRequestId == null || friendRequestId <= 0) {
            throw new IllegalArgumentException("친구 요청 페이지 경계가 필요합니다.");
        }
        String createdAtToken = ENCODER.encodeToString(
                createdAt.toString().getBytes(StandardCharsets.UTF_8));
        return PREFIX + "." + createdAtToken + "." + friendRequestId;
    }

    /** 요청 목록 커서를 검증하고 다음 keyset 조회 경계로 복원한다. */
    public RequestCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw new InvalidCursorException();
        }

        String[] parts = cursor.split("\\.", -1);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new InvalidCursorException();
        }

        try {
            String createdAtValue = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            LocalDateTime createdAt = LocalDateTime.parse(createdAtValue);
            long friendRequestId = Long.parseLong(parts[2]);
            if (friendRequestId <= 0) {
                throw new InvalidCursorException();
            }
            return new RequestCursor(createdAt, friendRequestId);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new InvalidCursorException(exception);
        }
    }
}
