package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** AI가 반환한 기록 ID를 현재 사용자의 활성 기록으로 제한한다. */
@Component
public class MemorySearchOwnedRecordFilter {

    private final RecordRepository recordRepository;

    /** 사용자 소유 기록 조회에 사용할 저장소를 연결한다. */
    public MemorySearchOwnedRecordFilter(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** ID를 한 번에 조회한 뒤 AI의 순서를 유지하며 무권한·삭제·중복 ID를 제거한다. */
    public List<Long> filter(Long userId, List<Long> recordIds) {
        List<Long> uniqueIds = new LinkedHashSet<>(recordIds).stream()
                .filter(id -> id != null)
                .toList();
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ownedIds = Set.copyOf(recordRepository.findActiveOwnedRecordIds(
                userId, uniqueIds));
        return uniqueIds.stream().filter(ownedIds::contains).toList();
    }
}
