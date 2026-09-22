package com.ktb4.team16.mulo.record.cursor;

import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RecordCursorCodec {
    private static final String DELIMITER = "|";
    private static final String SPLIT_DELIMITER = "\\|";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public String encode(LocalDateTime createdAt, Long recordId) {
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(recordId);

        String value = DATE_TIME_FORMATTER.format(createdAt) + DELIMITER + recordId;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public RecordCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidCursorException();
        }

        try {
            String value = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split(SPLIT_DELIMITER, -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InvalidCursorException();
            }

            LocalDateTime createdAt = LocalDateTime.parse(parts[0], DATE_TIME_FORMATTER);
            Long recordId = Long.valueOf(parts[1]);
            return new RecordCursor(createdAt, recordId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new InvalidCursorException(exception);
        }
    }
}
