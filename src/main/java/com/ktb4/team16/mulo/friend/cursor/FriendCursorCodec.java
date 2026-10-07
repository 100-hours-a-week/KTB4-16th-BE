package com.ktb4.team16.mulo.friend.cursor;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 닉네임·사용자 ID 정렬 키를 친구 목록 전용 opaque cursor로 변환한다. */
@Component
public final class FriendCursorCodec {

    private static final String PREFIX = "f1";
    private static final int MAX_CURSOR_LENGTH = 256;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** 서버가 반환할 다음 페이지 경계 커서를 만든다. */
    public String encode(String nickname, Long userId) {
        if (nickname == null || nickname.isBlank() || userId == null || userId <= 0) {
            throw new IllegalArgumentException("친구 페이지 경계가 필요합니다.");
        }
        String nicknameToken = ENCODER.encodeToString(nickname.getBytes(StandardCharsets.UTF_8));
        return PREFIX + "." + nicknameToken + "." + userId;
    }

    /** 친구 목록 커서를 검증하고 다음 keyset 조회 경계로 복원한다. */
    public FriendCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw new InvalidCursorException();
        }

        String[] parts = cursor.split("\\.", -1);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new InvalidCursorException();
        }

        try {
            byte[] nicknameBytes = DECODER.decode(parts[1]);
            String nickname = StandardCharsets.UTF_8.newDecoder().decode(
                    ByteBuffer.wrap(nicknameBytes)).toString();
            long userId = Long.parseLong(parts[2]);
            if (nickname.isBlank() || userId <= 0) {
                throw new InvalidCursorException();
            }
            return new FriendCursor(nickname, userId);
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw new InvalidCursorException(exception);
        }
    }
}
