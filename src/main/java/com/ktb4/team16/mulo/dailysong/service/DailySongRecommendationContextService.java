package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.dailysong.repository.DailySongRecordLocationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongRecordLocationRepository.Coordinates;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.NearbyTrack;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendationContext;
import com.ktb4.team16.mulo.recommendation.service.RecommendationNearbyTracksService;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaceContextService;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DailySongRecommendationContextService {
    private static final Coordinates SEOUL_CITY_HALL = new Coordinates(
            new BigDecimal("37.5663"), new BigDecimal("126.9779"));
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final DailySongRecordLocationRepository locationRepository;
    private final WeatherService weatherService;
    private final RecommendationPlaceContextService placeContextService;
    private final RecommendationNearbyTracksService nearbyTracksService;
    private final Clock clock;

    public DailySongRecommendationContextService(DailySongRecordLocationRepository locationRepository,
            WeatherService weatherService, RecommendationPlaceContextService placeContextService,
            RecommendationNearbyTracksService nearbyTracksService, Clock clock) {
        this.locationRepository = locationRepository;
        this.weatherService = weatherService;
        this.placeContextService = placeContextService;
        this.nearbyTracksService = nearbyTracksService;
        this.clock = clock;
    }

    public RecommendationContext build(Claim claim) {
        Coordinates location = locationRepository.findLatestValid(claim.userId(), claim.targetCutoffAt())
                .orElse(SEOUL_CITY_HALL);
        var weather = weatherService.getWeather(location.latitude().doubleValue(),
                location.longitude().doubleValue(), OffsetDateTime.now(clock));
        if (weather == null || weather.data() == null || weather.data().weatherCondition() == null) {
            throw new IllegalStateException("오늘의 노래 날씨 정보를 확인하지 못했습니다.");
        }
        String placeName = placeContextService.findLegalDongName(location.latitude(), location.longitude())
                .orElse(null);
        List<NearbyTrack> nearbyTracks = nearbyTracksService.findTopTracks(location.latitude(),
                        location.longitude()).stream()
                .map(track -> new NearbyTrack(track.title(), track.artistName(), track.count()))
                .toList();
        return new RecommendationContext(claim.userId(), weather.data().weatherCondition(),
                weather.data().temperature(), claim.recommendationDate().atTime(0, 30).atOffset(KST),
                nearbyTracks, placeName, List.of());
    }
}
