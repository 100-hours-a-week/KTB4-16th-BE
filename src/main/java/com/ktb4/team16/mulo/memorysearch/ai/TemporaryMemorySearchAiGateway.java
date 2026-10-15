package com.ktb4.team16.mulo.memorysearch.ai;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 로컬 개발에서만 실제 사용자 자물쇠를 이용해 AI 응답 형태를 임시로 만든다. */
@Component
@Profile("local")
@RequiredArgsConstructor
public class TemporaryMemorySearchAiGateway implements MemorySearchAiGateway {

    private final RecordRepository recordRepository;

    /** 질문·문맥을 아직 해석하지 않고 최신 활성 자물쇠 ID를 최대 50건 반환한다. */
    @Override
    public MemorySearchAiResult search(Long userId, String query, List<MemorySearchTurn> history) {
        List<Long> ids = recordRepository.findLatestActiveOwnedRecordIds(
                userId, PageRequest.ofSize(MemorySearchLimits.MAX_RESULT_RECORDS));
        return new MemorySearchAiResult("개발용 임시 검색 결과입니다.", ids, List.of());
    }
}
