package com.ktb4.team16.mulo.record.cursor;

import java.time.LocalDateTime;

public record RecordCursor(
        LocalDateTime createdAt,
        Long recordId
) {
}
