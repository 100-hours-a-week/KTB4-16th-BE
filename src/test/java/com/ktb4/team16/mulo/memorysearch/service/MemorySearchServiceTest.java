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
        when(music.getMusicTrackId()).thenReturn(id + 100);
        when(place.getPlaceId()).thenReturn(id + 200);
        when(place.getLegalDongName()).thenReturn("서울");
        return record;
    }
}
