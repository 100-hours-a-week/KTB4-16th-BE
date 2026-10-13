package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RecordCreationPreparationService {
    private final PlaceRepository placeRepository;
    private final KakaoRegionClient kakaoRegionClient;
    private final WeatherService weatherService;

    public RecordCreationPreparationService(PlaceRepository placeRepository,
            KakaoRegionClient kakaoRegionClient, WeatherService weatherService) {
        this.placeRepository = placeRepository;
        this.kakaoRegionClient = kakaoRegionClient;
        this.weatherService = weatherService;
    }

    public KakaoRegionClient.LegalRegion preparePlace(RecordCreateRequest request) {
        BigDecimal latitude = request.location().latitude();
        BigDecimal longitude = request.location().longitude();
        Optional<Place> existingPlace = placeRepository.findByLatitudeAndLongitude(
                latitude, longitude);
        if (existingPlace.isPresent()) {
            Place place = existingPlace.get();
            return new KakaoRegionClient.LegalRegion(
                    place.getLegalDongCode(), place.getLegalDongName());
        }

        return kakaoRegionClient.findLegalRegion(latitude, longitude);
    }

    public PreparedWeather prepareWeather(RecordCreateRequest request) {
        BigDecimal latitude = request.location().latitude();
        BigDecimal longitude = request.location().longitude();
        try {
            WeatherResponse weatherResponse = weatherService.getWeather(
                    latitude.doubleValue(), longitude.doubleValue(), OffsetDateTime.now());
            WeatherResponse.WeatherData weatherData = weatherResponse.data();
            Record.WeatherCondition weatherCondition = Record.WeatherCondition.valueOf(
                    weatherData.weatherCondition().name());
            return new PreparedWeather(weatherData.temperature(), weatherCondition);
        } catch (WeatherApiException exception) {
            return PreparedWeather.withoutWeather();
        }
    }
}
