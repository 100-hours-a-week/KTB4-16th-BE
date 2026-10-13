package com.ktb4.team16.mulo.friend.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class FriendCursorCodecTest {

    private static final String CODEC_CLASS =
            "com.ktb4.team16.mulo.friend.cursor.FriendCursorCodec";

    @Test
    void roundTripsUnicodeNicknameAndStableUserId() throws Exception {
        Object codec = createCodec();
        String encoded = (String) codec.getClass()
                .getMethod("encode", String.class, Long.class)
                .invoke(codec, "친구 이름", 35L);
        Object decoded = codec.getClass().getMethod("decode", String.class).invoke(codec, encoded);

        assertThat(decoded.getClass().getMethod("nickname").invoke(decoded)).isEqualTo("친구 이름");
        assertThat(decoded.getClass().getMethod("userId").invoke(decoded)).isEqualTo(35L);
    }

    @Test
    void rejectsMalformedAndRequestCursorValues() throws Exception {
        Object codec = createCodec();

        assertThatThrownBy(() -> decode(codec, "r1.dGVzdA.21"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decode(codec, "f1.!.0"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    /** 친구 페이지 커서 codec을 공개 생성자 계약으로 준비한다. */
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
