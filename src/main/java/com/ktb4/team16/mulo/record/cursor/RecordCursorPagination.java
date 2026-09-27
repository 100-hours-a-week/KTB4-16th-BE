package com.ktb4.team16.mulo.record.cursor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public final class RecordCursorPagination {
    public static final int PAGE_SIZE = 20;

    private RecordCursorPagination() {
    }

    public static int fetchSize() {
        return PAGE_SIZE + 1;
    }

    public static <T> CursorPage<T> paginate(
            List<T> records,
            RecordCursorCodec codec,
            Function<T, LocalDateTime> createdAtExtractor,
            Function<T, Long> recordIdExtractor
    ) {
        boolean hasNext = records.size() > PAGE_SIZE;
        List<T> pageRecords = hasNext ? records.subList(0, PAGE_SIZE) : records;

        String nextCursor = null;
        if (hasNext) {
            T lastRecord = pageRecords.get(pageRecords.size() - 1);
            nextCursor = codec.encode(
                    createdAtExtractor.apply(lastRecord),
                    recordIdExtractor.apply(lastRecord));
        }

        return new CursorPage<>(pageRecords, nextCursor);
    }

    public record CursorPage<T>(List<T> records, String nextCursor) {
    }
}
