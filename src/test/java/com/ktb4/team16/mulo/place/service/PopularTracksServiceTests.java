package com.ktb4.team16.mulo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PopularTracksServiceTests {

    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final PlaceService placeService = new PlaceService(recordRepository);

    @Test
    void aggregatesTracksAndAssignsRankFromRepositoryOrder() {
        List<Long> placeIds = List.of(10L, 20L);
        PopularTrackAggregateDto first = new PopularTrackAggregateDto(
                3L, "first", "artist", 4L, LocalDateTime.now());
        PopularTrackAggregateDto second = new PopularTrackAggregateDto(
                8L, "second", "artist", 2L, LocalDateTime.now().minusHours(1));
        when(recordRepository.countActiveRecordsAtPlaces(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(6L);
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        PopularTracksResponseDto result = placeService.getPopularTracks(placeIds);

        assertThat(result.recordCount()).isEqualTo(6L);
        assertThat(result.music()).extracting(PopularTracksResponseDto.Music::rank)
                .containsExactly(1, 2);
        assertThat(result.music()).extracting(PopularTracksResponseDto.Music::musicTrackId)
                .containsExactly(3L, 8L);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recordRepository).countActiveRecordsAtPlaces(eq(placeIds), cutoff.capture());
        verify(recordRepository).findPopularTrackAggregates(eq(placeIds), eq(cutoff.getValue()));
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    @Test
    void returnsEmptyMusicWhenNoActiveRecordsExist() {
        List<Long> placeIds = List.of(10L);
        when(recordRepository.countActiveRecordsAtPlaces(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(List.of());

        PopularTracksResponseDto result = placeService.getPopularTracks(placeIds);

        assertThat(result.recordCount()).isZero();
        assertThat(result.music()).isEmpty();
    }
}
