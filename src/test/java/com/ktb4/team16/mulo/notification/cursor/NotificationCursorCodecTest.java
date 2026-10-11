package com.ktb4.team16.mulo.notification.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class NotificationCursorCodecTest {

    private final NotificationCursorCodec codec = new NotificationCursorCodec();

    /** 같은 생성 시각의 알림도 ID를 기준으로 안정적으로 다음 페이지를 이어 가는지 검증한다. */
    @Test
    void roundTripsTimestampAndNotificationId() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 11, 9, 30, 15, 123_000_000);

        String encoded = codec.encode(createdAt, 501L);

        assertThat(codec.decode(encoded)).isEqualTo(new NotificationCursor(createdAt, 501L));
    }

    /** 손상된 커서와 비양수 ID가 조회 경계를 바꾸지 않도록 400 오류로 거부한다. */
    @Test
    void rejectsMalformedCursorAndNonPositiveNotificationId() {
        assertThatThrownBy(() -> codec.decode("not-a-cursor"))
                .isInstanceOf(InvalidCursorException.class);
        String zeroIdCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "2026-10-11T09:30|0".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> codec.decode(zeroIdCursor))
                .isInstanceOf(InvalidCursorException.class);
    }
}
