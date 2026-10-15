package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySearchOwnedRecordFilterTest {

    @Mock
    private RecordRepository recordRepository;

    /** AI 순서를 유지하면서 무권한·삭제·중복 ID를 제외한다. */
    @Test
    void keepsAiOrderAndDropsForeignDeletedOrDuplicateIds() {
        when(recordRepository.findActiveOwnedRecordIds(eq(35L), anyList()))
                .thenReturn(List.of(103L, 101L));

        var filter = new MemorySearchOwnedRecordFilter(recordRepository);

        assertThat(filter.filter(35L, List.of(101L, 999L, 103L, 101L, 555L)))
                .containsExactly(101L, 103L);
    }

    /** AI가 빈 결과를 주면 불필요한 DB 조회를 하지 않는다. */
    @Test
    void emptyAiResultsDoNotQueryDatabase() {
        var filter = new MemorySearchOwnedRecordFilter(recordRepository);

        assertThat(filter.filter(35L, List.of())).isEmpty();
        verifyNoInteractions(recordRepository);
    }
}
