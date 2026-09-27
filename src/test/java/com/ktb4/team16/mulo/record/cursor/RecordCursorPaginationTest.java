package com.ktb4.team16.mulo.record.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordCursorPaginationTest {
    private final RecordCursorCodec codec = mock(RecordCursorCodec.class);

    @Test
    void keepsUpToPageSizeRecordsWithoutNextCursor() {
        List<Item> records = records(RecordCursorPagination.PAGE_SIZE);

        RecordCursorPagination.CursorPage<Item> page = paginate(records);

        assertThat(page.records()).hasSize(RecordCursorPagination.PAGE_SIZE);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void trimsExtraRecordAndCreatesCursorFromLastResponseRecord() {
        List<Item> records = records(RecordCursorPagination.PAGE_SIZE + 1);
        Item lastResponseRecord = records.get(RecordCursorPagination.PAGE_SIZE - 1);
        when(codec.encode(lastResponseRecord.createdAt(), lastResponseRecord.recordId()))
                .thenReturn("next-cursor");

        RecordCursorPagination.CursorPage<Item> page = paginate(records);

        assertThat(page.records()).hasSize(RecordCursorPagination.PAGE_SIZE);
        assertThat(page.nextCursor()).isEqualTo("next-cursor");
        verify(codec).encode(lastResponseRecord.createdAt(), lastResponseRecord.recordId());
    }

    private RecordCursorPagination.CursorPage<Item> paginate(List<Item> records) {
        return RecordCursorPagination.paginate(
                records, codec, Item::createdAt, Item::recordId);
    }

    private List<Item> records(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(id -> new Item(
                        LocalDateTime.of(2026, 9, 19, 15, 30).minusMinutes(id),
                        (long) id))
                .toList();
    }

    private record Item(LocalDateTime createdAt, Long recordId) {
    }
}
