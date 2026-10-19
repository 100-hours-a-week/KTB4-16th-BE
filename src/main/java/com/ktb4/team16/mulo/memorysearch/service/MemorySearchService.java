package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiGateway;
import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiResult;
import com.ktb4.team16.mulo.memorysearch.controller.MemorySearchCard;
import com.ktb4.team16.mulo.memorysearch.controller.MemorySearchResult;
import com.ktb4.team16.mulo.memorysearch.controller.MemorySearchSuggestedCard;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 대화 문맥, 실제 AI 결과, 소유권 재검증과 카드 공개를 조정한다. */
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private static final String NO_DISPLAYABLE_RECORDS_ANSWER = "표시할 수 있는 자물쇠가 없어요.";

    private final MemorySearchConversationStore store;
    private final MemorySearchRequestGate gate;
    private final MemorySearchRecordAvailability availability;
    private final MemorySearchOwnedRecordFilter ownedRecordFilter;
    private final RecordRepository recordRepository;
    private final MemorySearchAiGateway aiGateway;

    /** 현재 질문을 처리하고 공개 가능한 카드만 AI 순서로 응답·문맥에 남긴다. */
    public MemorySearchResult search(Long userId, String conversationId, String query) {
        try (var ignored = gate.acquire(userId, conversationId)) {
            availability.requireAnyOwnedRecord(userId);
            List<MemorySearchTurn> history = store.read(userId, conversationId)
                    .orElseThrow(() -> new MemorySearchDomainException(
                            ErrorCode.MEMORY_SEARCH_CONVERSATION_GONE));
            MemorySearchAiResult aiResult = aiGateway.search(userId, query, history);
            List<Long> exactIds = ownedRecordFilter.filter(userId, aiResult.recordIds());
            List<Long> suggestedIds = ownedRecordFilter.filter(userId,
                    aiResult.suggestedRecords().stream()
                            .map(MemorySearchAiResult.SuggestedRecord::recordId).toList());
            Map<Long, MemorySearchCard> cards = loadCards(userId, exactIds, suggestedIds);
            // 소유권 확인 뒤 카드 재조회 사이에 삭제된 자물쇠는 맵에 없으므로 제외한다.
            List<MemorySearchCard> exactCards = exactIds.stream()
                    .map(cards::get).filter(card -> card != null).toList();
            List<MemorySearchSuggestedCard> suggestedCards = aiResult.suggestedRecords()
                    .stream().filter(item -> suggestedIds.contains(item.recordId()))
                    .filter(item -> cards.containsKey(item.recordId()))
                    .map(item -> MemorySearchSuggestedCard.from(
                            cards.get(item.recordId()), item.matchedConditions()))
                    .toList();
            List<Long> publishedIds = exactCards.stream()
                    .map(MemorySearchCard::recordId).toList();
            if (!store.appendIfActive(userId, conversationId,
                    new MemorySearchTurn(query, publishedIds))) {
                throw new MemorySearchDomainException(ErrorCode.MEMORY_SEARCH_CONVERSATION_GONE);
            }
            // AI가 정확 일치 결과를 보냈지만 검증 후 모두 제외되면 찾았다는 문구를 교체한다.
            String answer = !aiResult.recordIds().isEmpty() && exactCards.isEmpty()
                    ? NO_DISPLAYABLE_RECORDS_ANSWER : aiResult.answer();
            return new MemorySearchResult(answer, exactCards, suggestedCards);
        }
    }

    /** 두 후보 배열을 한 번의 DB 조회로 카드화하고 AI 순서 재배열에 사용할 맵을 만든다. */
    private Map<Long, MemorySearchCard> loadCards(Long userId, List<Long> exactIds,
            List<Long> suggestedIds) {
        List<Long> ids = java.util.stream.Stream.concat(exactIds.stream(),
                suggestedIds.stream()).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemorySearchCard> cards = new HashMap<>();
        for (Record record : recordRepository.findActiveOwnedRecordsForSearch(userId, ids)) {
            cards.put(record.getRecordId(), MemorySearchCard.from(record));
        }
        return cards;
    }
}
