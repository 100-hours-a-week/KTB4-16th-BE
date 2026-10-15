package com.ktb4.team16.mulo.memorysearch.ai;

import java.util.List;

/** AI가 선택한 정확 일치 ID와 일부 조건 제안 및 건수 없는 답변이다. */
public record MemorySearchAiResult(String answer, List<Long> recordIds,
        List<SuggestedRecord> suggestedRecords) {

    /** 게이트웨이 결과를 불변 목록으로 복사해 후속 변형을 막는다. */
    public MemorySearchAiResult {
        recordIds = List.copyOf(recordIds);
        suggestedRecords = List.copyOf(suggestedRecords);
    }

    /** 제안 카드의 자물쇠 ID와 AI가 설명한 일치 조건이다. */
    public record SuggestedRecord(Long recordId, List<String> matchedConditions) {

        /** 제안 조건 배열을 불변 값으로 보관한다. */
        public SuggestedRecord {
            matchedConditions = List.copyOf(matchedConditions);
        }
    }
}
