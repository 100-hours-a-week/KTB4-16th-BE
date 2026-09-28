package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationNearbyTrackQueryService {
    private static final int NEARBY_TRACK_LIMIT = 5;
    private final RecordRepository recordRepository;
    private final Clock clock;

    // 선택한 지도 장소의 최근 7일 인기 음악을 AI 문맥용 상위 다섯 곡으로 변환한다.
    @Transactional(readOnly = true)
    public List<RecommendationAiClient.NearbyTrack> findTopTracks(List<Long> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(7);
        return recordRepository.findPopularTrackAggregates(placeIds, cutoff).stream()
                .limit(NEARBY_TRACK_LIMIT)
                .map(track -> new RecommendationAiClient.NearbyTrack(
                        track.title(), track.artistName(), track.count()))
                .toList();
    }
}
