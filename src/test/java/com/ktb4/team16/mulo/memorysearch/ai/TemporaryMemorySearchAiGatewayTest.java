package com.ktb4.team16.mulo.memorysearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class TemporaryMemorySearchAiGatewayTest {

    /** 임시 AI는 현재 사용자의 최신 활성 자물쇠 ID만 최대 50건 반환한다. */
    @Test
    void returnsOwnedRecordIdsInRepositoryRelevanceOrder() {
        RecordRepository repository = mock(RecordRepository.class);
        when(repository.findLatestActiveOwnedRecordIds(35L,
                PageRequest.ofSize(MemorySearchLimits.MAX_RESULT_RECORDS)))
                .thenReturn(List.of(9L, 2L, 7L));
        var gateway = new TemporaryMemorySearchAiGateway(repository);

        var result = gateway.search(35L, "가을에 들은 노래", List.of());

        assertThat(result.recordIds()).containsExactly(9L, 2L, 7L);
        assertThat(result.suggestedRecords()).isEmpty();
        assertThat(result.answer()).doesNotContain("3건");
    }
}
