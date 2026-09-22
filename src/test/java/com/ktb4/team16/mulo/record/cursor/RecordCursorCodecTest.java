package com.ktb4.team16.mulo.record.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RecordCursorCodecTest {
    private final RecordCursorCodec codec = new RecordCursorCodec();

    @Test
    void encodesAndDecodesCursor() {
        LocalDateTime createdAt = LocalDateTime.parse("2026-09-19T15:30:00");
        Long recordId = 101L;

        String encoded = codec.encode(createdAt, recordId);
        RecordCursor decoded = codec.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.recordId()).isEqualTo(recordId);
    }

    @Test
    void rejectsInvalidBase64() {
        assertInvalidCursor("not-valid-base64!");
    }

    @Test
    void rejectsValueWithoutDelimiter() {
        assertInvalidCursor(encodeRaw("2026-09-19T15:30:00"));
    }

    @Test
    void rejectsInvalidCreatedAt() {
        assertInvalidCursor(encodeRaw("invalid-created-at|101"));
    }

    @Test
    void rejectsInvalidRecordId() {
        assertInvalidCursor(encodeRaw("2026-09-19T15:30:00|invalid-record-id"));
    }

    @Test
    void rejectsEmptyValue() {
        assertInvalidCursor(encodeRaw("|101"));
        assertInvalidCursor(encodeRaw("2026-09-19T15:30:00|"));
    }

    @Test
    void rejectsValueWithMoreThanTwoParts() {
        assertInvalidCursor(encodeRaw("2026-09-19T15:30:00|101|extra"));
    }

    private void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(InvalidCursorException.class);
    }

    private String encodeRaw(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
