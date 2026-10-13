package com.ktb4.team16.mulo.friend.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RequestCursorCodecTest {

    private static final String CODEC_CLASS =
            "com.ktb4.team16.mulo.friend.cursor.RequestCursorCodec";

    @Test
    void roundTripsTimestampAndRequestId() throws Exception {
        Object codec = createCodec();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 6, 12, 30, 45, 123_000_000);
        String encoded = (String) codec.getClass()
                .getMethod("encode", LocalDateTime.class, Long.class)
                .invoke(codec, createdAt, 21L);
        Object decoded = codec.getClass().getMethod("decode", String.class).invoke(codec, encoded);

        assertThat(decoded.getClass().getMethod("createdAt").invoke(decoded)).isEqualTo(createdAt);
        assertThat(decoded.getClass().getMethod("friendRequestId").invoke(decoded)).isEqualTo(21L);
    }

    @Test
    void rejectsFriendCursorAndInvalidRequestIds() throws Exception {
        Object codec = createCodec();

        assertThatThrownBy(() -> decode(codec, "f1.7ZWc.35"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decode(codec, "r1.d2VpcmQ.0"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    /** 요청 페이지 커서 codec을 공개 생성자 계약으로 준비한다. */
    private static Object createCodec() throws Exception {
        Class<?> codecType = Class.forName(CODEC_CLASS);
        Constructor<?> constructor = codecType.getConstructor();
        return constructor.newInstance();
    }

    /** 테스트 커서가 decode 입력 검증을 통과하는지 실행한다. */
    private static Object decode(Object codec, String cursor) throws Exception {
        Method method = codec.getClass().getMethod("decode", String.class);
        return method.invoke(codec, cursor);
    }
}
