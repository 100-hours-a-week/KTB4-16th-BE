package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.record.dto.NearbyTrackCandidateDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationNearbyTracksService {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double RADIUS_METERS = 1_000;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final int MAX_TRACKS = 5;

    private final RecordRepository recordRepository;
    private final Clock clock;

    // 현재 좌표 반경 1km의 최근 활성 자물쇠를 음악별로 집계해 AI 문맥용 상위 5곡을 만든다.
    public List<NearbyTrack> findTopTracks(BigDecimal latitude, BigDecimal longitude) {
        BoundingBox boundingBox = boundingBox(latitude, longitude);
        LocalDateTime createdAtFrom = LocalDateTime.now(clock).minusDays(7);
        Map<Long, Aggregate> aggregates = new LinkedHashMap<>();

        for (NearbyTrackCandidateDto candidate : recordRepository.findNearbyTrackCandidates(
                boundingBox.swLatitude(), boundingBox.neLatitude(), boundingBox.swLongitude(),
                boundingBox.neLongitude(), createdAtFrom)) {
            if (isWithinRadius(latitude, longitude, candidate.latitude(), candidate.longitude())) {
                aggregates.compute(candidate.musicTrackId(), (ignored, aggregate) ->
                        aggregate == null ? Aggregate.from(candidate) : aggregate.add(candidate));
            }
        }

        return aggregates.values().stream()
                .sorted(Comparator.comparingLong(Aggregate::count).reversed()
                        .thenComparing(Aggregate::latestCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Aggregate::musicTrackId))
                .limit(MAX_TRACKS)
                .map(Aggregate::toNearbyTrack)
                .toList();
    }

    // DB 후보를 줄이기 위한 위도·경도 사각 범위를 계산한다.
    private BoundingBox boundingBox(BigDecimal latitude, BigDecimal longitude) {
        double latitudeDelta = RADIUS_METERS / METERS_PER_LATITUDE_DEGREE;
        double cosine = Math.cos(Math.toRadians(latitude.doubleValue()));
        double longitudeDelta = RADIUS_METERS / (METERS_PER_LATITUDE_DEGREE * Math.max(cosine, 0.000001));
        return new BoundingBox(offset(latitude, -latitudeDelta), offset(latitude, latitudeDelta),
                offset(longitude, -longitudeDelta), offset(longitude, longitudeDelta));
    }

    // 하버사인 공식으로 후보가 실제 원형 반경 안에 있는지 판정한다.
    private boolean isWithinRadius(BigDecimal originLatitude, BigDecimal originLongitude,
            BigDecimal candidateLatitude, BigDecimal candidateLongitude) {
        double latitudeDifference = Math.toRadians(candidateLatitude.doubleValue() - originLatitude.doubleValue());
        double longitudeDifference = Math.toRadians(candidateLongitude.doubleValue() - originLongitude.doubleValue());
        double latitudeStart = Math.toRadians(originLatitude.doubleValue());
        double latitudeEnd = Math.toRadians(candidateLatitude.doubleValue());
        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(latitudeStart) * Math.cos(latitudeEnd)
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
                <= RADIUS_METERS;
    }

    // BigDecimal 좌표에 경계값을 더해 저장소 쿼리에 사용할 정밀도를 유지한다.
    private BigDecimal offset(BigDecimal coordinate, double delta) {
        return coordinate.add(BigDecimal.valueOf(delta)).setScale(7, RoundingMode.HALF_UP);
    }

    private record BoundingBox(BigDecimal swLatitude, BigDecimal neLatitude,
            BigDecimal swLongitude, BigDecimal neLongitude) { }

    // 같은 음악의 수와 최신 자물쇠 시각을 누적해 안정적인 인기곡 정렬 값을 만든다.
    private record Aggregate(Long musicTrackId, String title, String artistName, long count,
            LocalDateTime latestCreatedAt) {
        private static Aggregate from(NearbyTrackCandidateDto candidate) {
            return new Aggregate(candidate.musicTrackId(), candidate.title(), candidate.artistName(), 1,
                    candidate.createdAt());
        }

        private Aggregate add(NearbyTrackCandidateDto candidate) {
            return new Aggregate(musicTrackId, title, artistName, count + 1,
                    latestCreatedAt.isAfter(candidate.createdAt()) ? latestCreatedAt : candidate.createdAt());
        }

        private NearbyTrack toNearbyTrack() {
            return new NearbyTrack(title, artistName, count);
        }
    }

    // AI 명세의 nearbyTracks 한 항목에 필요한 값만 노출한다.
    public record NearbyTrack(String title, String artistName, long count) { }
}
