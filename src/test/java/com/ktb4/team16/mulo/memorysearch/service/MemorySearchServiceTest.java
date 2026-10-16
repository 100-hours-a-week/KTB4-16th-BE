package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiGateway;
import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiResult;
import com.ktb4.team16.mulo.memorysearch.session.InMemoryMemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemorySearchServiceTest {

    // 자물쇠 ID와 연관 엔티티 ID를 구분하는 테스트 데이터용 오프셋이다.
    private static final long MUSIC_TRACK_ID_OFFSET = 100L;
    private static final long PLACE_ID_OFFSET = 200L;

    /** DB 순서가 달라도 AI 관련도 순서로 공개하고 검증된 ID만 다음 문맥에 보관한다. */
    @Test
    void preservesAiOrderAfterOwnershipFilteringAndStoresVerifiedHistory() {
        RecordRepository repository = mock(RecordRepository.class);
        when(repository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L))
                .thenReturn(true);
        when(repository.findActiveOwnedRecordIds(35L, List.of(9L, 2L, 7L)))
                .thenReturn(List.of(2L, 9L));
        Record second = record(2L, "두 번째");
        Record first = record(9L, "첫 번째");
        when(repository.findActiveOwnedRecordsForSearch(35L, List.of(9L, 2L)))
                .thenReturn(List.of(second, first));
        var store = new InMemoryMemorySearchConversationStore(Duration.ofMinutes(30));
        String conversationId = store.start(35L);
        MemorySearchAiGateway ai = (userId, query, history) ->
                new MemorySearchAiResult("기억을 살펴봤어요.", List.of(9L, 2L, 7L), List.of());
        var service = new MemorySearchService(store,
                new MemorySearchRequestGate(store, new MemorySearchInFlightRegistry()),
                new MemorySearchRecordAvailability(repository),
                new MemorySearchOwnedRecordFilter(repository), repository, ai);

        var response = service.search(35L, conversationId, "가을 노래");

        assertThat(response.records()).extracting(card -> card.recordId())
                .containsExactly(9L, 2L);
        assertThat(response.records()).extracting(card -> card.title())
                .containsExactly("첫 번째", "두 번째");
        assertThat(store.read(35L, conversationId).orElseThrow())
                .containsExactly(new MemorySearchTurn("가을 노래", List.of(9L, 2L)));
    }

    /** AI의 정확 일치 ID가 검증에서 모두 빠지면 답변을 교체하고 제안 카드는 유지한다. */
    @Test
    void replacesAnswerWhenAllExactMatchesAreFilteredOut() {
        RecordRepository repository = mock(RecordRepository.class);
        when(repository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L))
                .thenReturn(true);
        when(repository.findActiveOwnedRecordIds(35L, List.of(9L)))
                .thenReturn(List.of());
        when(repository.findActiveOwnedRecordIds(35L, List.of(2L)))
                .thenReturn(List.of(2L));
        Record suggestedRecord = record(2L, "제안 카드");
        when(repository.findActiveOwnedRecordsForSearch(35L, List.of(2L)))
                .thenReturn(List.of(suggestedRecord));
        var store = new InMemoryMemorySearchConversationStore(Duration.ofMinutes(30));
        String conversationId = store.start(35L);
        MemorySearchAiGateway ai = (userId, query, history) -> new MemorySearchAiResult(
                "조건에 맞는 자물쇠를 찾았어요.", List.of(9L),
                List.of(new MemorySearchAiResult.SuggestedRecord(2L, List.of("가을"))));
        var service = new MemorySearchService(store,
                new MemorySearchRequestGate(store, new MemorySearchInFlightRegistry()),
                new MemorySearchRecordAvailability(repository),
                new MemorySearchOwnedRecordFilter(repository), repository, ai);

        var response = service.search(35L, conversationId, "가을 노래");

        assertThat(response.answer()).isEqualTo("표시할 수 있는 자물쇠가 없어요.");
        assertThat(response.records()).isEmpty();
        assertThat(response.suggestedRecords()).extracting(card -> card.recordId())
                .containsExactly(2L);
        assertThat(store.read(35L, conversationId).orElseThrow())
                .containsExactly(new MemorySearchTurn("가을 노래", List.of()));
    }

    /** AI가 처음부터 정확 일치가 없다고 판단한 경우에는 조건 설명을 유지한다. */
    @Test
    void preservesAiAnswerWhenThereWereNoExactMatchesBeforeFiltering() {
        RecordRepository repository = mock(RecordRepository.class);
        when(repository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L))
                .thenReturn(true);
        var store = new InMemoryMemorySearchConversationStore(Duration.ofMinutes(30));
        String conversationId = store.start(35L);
        MemorySearchAiGateway ai = (userId, query, history) -> new MemorySearchAiResult(
                "모든 조건에 맞는 자물쇠는 없어요.", List.of(), List.of());
        var service = new MemorySearchService(store,
                new MemorySearchRequestGate(store, new MemorySearchInFlightRegistry()),
                new MemorySearchRecordAvailability(repository),
                new MemorySearchOwnedRecordFilter(repository), repository, ai);

        var response = service.search(35L, conversationId, "가을 노래");

        assertThat(response.answer()).isEqualTo("모든 조건에 맞는 자물쇠는 없어요.");
    }

    /** 카드 데이터는 AI 문구가 아닌 DB 기록·음악·장소에서 읽는다. */
    private Record record(Long id, String title) {
        Record record = mock(Record.class);
        MusicTrack music = mock(MusicTrack.class);
        Place place = mock(Place.class);
        when(record.getRecordId()).thenReturn(id);
        when(record.getMusicTrack()).thenReturn(music);
        when(record.getPlace()).thenReturn(place);
        when(record.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 15, 0, 0));
        when(music.getTitle()).thenReturn(title);
        when(music.getArtistName()).thenReturn("가수");
        when(music.getAlbumImageUrl()).thenReturn("");
        when(music.getMusicTrackId()).thenReturn(id + MUSIC_TRACK_ID_OFFSET);
        when(place.getPlaceId()).thenReturn(id + PLACE_ID_OFFSET);
        when(place.getLegalDongName()).thenReturn("서울");
        return record;
    }
}
