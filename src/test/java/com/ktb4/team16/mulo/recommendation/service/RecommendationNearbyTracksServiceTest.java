package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.dto.NearbyTrackCandidateDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
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
class RecommendationNearbyTracksServiceTest {
    @Mock private RecordRepository recordRepository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    // 반경 1km 밖 후보를 제외하고 동일 음악을 최근 자물쇠 수로 집계한다.
    @Test
    void returnsOnlyTracksWithinOneKilometerOrderedByCountThenLatestRecord() {
        when(recordRepository.findNearbyTrackCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        candidate(2L, "밖의 노래", "A", "37.5770", "126.9780", "2026-09-27T20:00:00"),
                        candidate(20L, "동률 뒤", "B", "37.5666", "126.9780", "2026-09-27T20:00:00"),
                        candidate(10L, "가장 인기", "C", "37.5665", "126.9780", "2026-09-27T19:00:00"),
                        candidate(10L, "가장 인기", "C", "37.5667", "126.9780", "2026-09-27T21:00:00"),
                        candidate(30L, "동률 먼저", "D", "37.5664", "126.9780", "2026-09-27T21:00:00")));

        var result = service().findTopTracks(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780));

        assertThat(result).extracting(RecommendationNearbyTracksService.NearbyTrack::title)
                .containsExactly("가장 인기", "동률 먼저", "동률 뒤");
        assertThat(result.getFirst().count()).isEqualTo(2);
    }

    // 후보가 없으면 AI 요청에서 nearbyTracks를 생략할 수 있도록 빈 목록을 반환한다.
    @Test
    void returnsEmptyListWhenThereAreNoNearbyCandidates() {
        when(recordRepository.findNearbyTrackCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service().findTopTracks(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780))).isEmpty();
    }

    // 서비스의 저장소·시간 의존성을 명시적으로 조립한다.
    private RecommendationNearbyTracksService service() {
        return new RecommendationNearbyTracksService(recordRepository, clock);
    }

    // 반경·집계 테스트가 읽기 쉬운 후보 자물쇠를 생성한다.
    private NearbyTrackCandidateDto candidate(Long musicTrackId, String title, String artistName,
            String latitude, String longitude, String createdAt) {
        return new NearbyTrackCandidateDto(musicTrackId, title, artistName,
                new BigDecimal(latitude), new BigDecimal(longitude), LocalDateTime.parse(createdAt));
    }
}
