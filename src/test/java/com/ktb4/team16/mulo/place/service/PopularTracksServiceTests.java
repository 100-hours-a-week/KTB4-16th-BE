package com.ktb4.team16.mulo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    /** 기존 전체 집계의 부분합으로 건수를 구하고 별도 COUNT 조회를 생략하는지 검증한다. */
    @Test
    void calculatesTotalFromCompleteGroupsWithoutSeparateCount() {
        List<Long> placeIds = List.of(10L, 20L);
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new PopularTrackAggregateDto(3L, "곡", "가수", 4L,
                                LocalDateTime.now(), "https://example.invalid/3"),
                        new PopularTrackAggregateDto(8L, "다른 곡", "가수", 2L,
                                LocalDateTime.now(), "https://example.invalid/8")));

        PopularTracksResponseDto result = placeService.getPopularTracks(placeIds);

        assertThat(result.recordCount()).isEqualTo(6L);
        verify(recordRepository, never()).countActiveRecordsAtPlaces(any(), any());
    }

    /** 합계가 int 범위를 넘더라도 기존 Long 응답의 건수를 유지하는지 검증한다. */
    @Test
    void keepsLongPrecisionWhenGroupTotalExceedsIntegerRange() {
        when(recordRepository.findPopularTrackAggregates(any(), any()))
                .thenReturn(List.of(
                        new PopularTrackAggregateDto(1L, "곡", "가수", 2_147_483_647L,
                                LocalDateTime.now(), "https://example.invalid/1"),
                        new PopularTrackAggregateDto(2L, "곡2", "가수", 3L,
                                LocalDateTime.now(), "https://example.invalid/2")));

        assertThat(placeService.getPopularTracks(List.of(10L)).recordCount())
                .isEqualTo(2_147_483_650L);
    }

    @Test
    void aggregatesTracksAndAssignsRankFromRepositoryOrder() {
        List<Long> placeIds = List.of(10L, 20L);
        PopularTrackAggregateDto first = new PopularTrackAggregateDto(
                3L, "first", "artist", 4L, LocalDateTime.now(), "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");
        PopularTrackAggregateDto second = new PopularTrackAggregateDto(
                8L, "second", "artist", 2L, LocalDateTime.now().minusHours(1), "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC");
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        PopularTracksResponseDto result = placeService.getPopularTracks(placeIds);

        assertThat(result.recordCount()).isEqualTo(6L);
        assertThat(result.music()).extracting(PopularTracksResponseDto.Music::rank)
                .containsExactly(1, 2);
        assertThat(result.music()).extracting(PopularTracksResponseDto.Music::musicTrackId)
                .containsExactly(3L, 8L);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recordRepository).findPopularTrackAggregates(eq(placeIds), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    @Test
    void returnsEmptyMusicWhenNoActiveRecordsExist() {
        List<Long> placeIds = List.of(10L);
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), any(LocalDateTime.class)))
                .thenReturn(List.of());

        PopularTracksResponseDto result = placeService.getPopularTracks(placeIds);

        assertThat(result.recordCount()).isZero();
        assertThat(result.music()).isEmpty();
    }
}
