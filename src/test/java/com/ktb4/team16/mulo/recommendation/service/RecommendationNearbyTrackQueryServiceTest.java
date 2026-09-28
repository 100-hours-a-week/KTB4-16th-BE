package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationNearbyTrackQueryServiceTest {
    @Mock private RecordRepository recordRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-28T05:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    // 선택한 장소들의 기존 인기 순서를 유지하며 AI에는 상위 다섯 곡만 전달한다.
    @Test
    void returnsFirstFiveRecentPopularTracksForSelectedPlaces() {
        List<Long> placeIds = List.of(10L, 20L);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 21, 14, 0);
        when(recordRepository.findPopularTrackAggregates(eq(placeIds), eq(cutoff))).thenReturn(List.of(
                aggregate(1L, "곡1", "가수1", 9L),
                aggregate(2L, "곡2", "가수2", 8L),
                aggregate(3L, "곡3", "가수3", 7L),
                aggregate(4L, "곡4", "가수4", 6L),
                aggregate(5L, "곡5", "가수5", 5L),
                aggregate(6L, "곡6", "가수6", 4L)));

        var tracks = new RecommendationNearbyTrackQueryService(recordRepository, clock)
                .findTopTracks(placeIds);

        assertThat(tracks).extracting(track -> track.title()).containsExactly(
                "곡1", "곡2", "곡3", "곡4", "곡5");
        assertThat(tracks.get(0).artistName()).isEqualTo("가수1");
        assertThat(tracks.get(0).count()).isEqualTo(9L);
        verify(recordRepository).findPopularTrackAggregates(placeIds, cutoff);
    }

    // 장소 선택이 없으면 DB 집계 없이 AI 선택 문맥도 비워 둔다.
    @Test
    void returnsEmptyTracksWithoutSelectedPlaces() {
        var tracks = new RecommendationNearbyTrackQueryService(recordRepository, clock)
                .findTopTracks(List.of());

        assertThat(tracks).isEmpty();
        verifyNoInteractions(recordRepository);
    }

    // 기존 인기 음악 집계 DTO를 테스트 입력으로 간결하게 만든다.
    private PopularTrackAggregateDto aggregate(Long id, String title, String artist, Long count) {
        return new PopularTrackAggregateDto(id, title, artist, count,
                LocalDateTime.of(2026, 9, 28, 13, 0));
    }
}
