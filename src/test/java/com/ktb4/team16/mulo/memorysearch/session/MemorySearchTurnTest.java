package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class MemorySearchTurnTest {

    /** 한 턴의 정확 일치 ID가 50개를 넘으면 V3 문맥으로 저장하지 않는다. */
    @Test
    void rejectsMoreThanFiftyRecordIds() {
        var ids = LongStream.rangeClosed(1, MemorySearchLimits.MAX_RESULT_RECORDS + 1)
                .boxed().toList();

        assertThatThrownBy(() -> new MemorySearchTurn("가을 노래", ids))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
