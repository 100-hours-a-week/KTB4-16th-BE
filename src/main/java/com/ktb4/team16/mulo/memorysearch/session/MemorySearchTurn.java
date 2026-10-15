package com.ktb4.team16.mulo.memorysearch.session;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import java.util.List;
import java.util.Objects;

/** V3 요청 준비를 위해 보관하는 질문과 검증된 정확 일치 기록 ID다. */
public record MemorySearchTurn(String query, List<Long> recordIds) {

    /** 저장소에 보관할 턴을 불변 값으로 복사하고 필수 항목을 확인한다. */
    public MemorySearchTurn {
        Objects.requireNonNull(query);
        recordIds = List.copyOf(recordIds);
        if (recordIds.size() > MemorySearchLimits.MAX_RESULT_RECORDS) {
            throw new IllegalArgumentException("A memory search turn can hold at most "
                    + MemorySearchLimits.MAX_RESULT_RECORDS + " record IDs");
        }
    }
}
