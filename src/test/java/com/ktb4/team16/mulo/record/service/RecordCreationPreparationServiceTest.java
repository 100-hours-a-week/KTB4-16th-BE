package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherCacheWriter;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class RecordCreationPreparationServiceTest {
    private static final RecordCreateRequest REQUEST = new RecordCreateRequest(
            new RecordCreateRequest.Location(
                    new BigDecimal("37.5000001"), new BigDecimal("127.0000000"),
                    "client-code", "client-name"),
            new RecordCreateRequest.Music("track-id", "title", "artist", "album", "url"),
            10, "comment", 11L);

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final KakaoRegionClient kakaoRegionClient = mock(KakaoRegionClient.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final RecordCreationPreparationService preparationService =
            new RecordCreationPreparationService(
                    placeRepository, kakaoRegionClient, weatherService);

    @Test
    void reusesExistingPlaceRegionWithoutDelayOrKakaoCall() {
        Place existing = mock(Place.class);
        when(placeRepository.findByLatitudeAndLongitude(
                REQUEST.location().latitude(), REQUEST.location().longitude()))
                .thenReturn(Optional.of(existing));
        when(existing.getLegalDongCode()).thenReturn("server-code");
        when(existing.getLegalDongName()).thenReturn("server-name");

        KakaoRegionClient.LegalRegion region = preparationService.preparePlace(REQUEST);

        assertThat(region).isEqualTo(new KakaoRegionClient.LegalRegion("server-code", "server-name"));
        verifyNoInteractions(kakaoRegionClient);
    }

    @Test
    void callsKakaoOnlyAfterPlaceMiss() {
        KakaoRegionClient.LegalRegion expected =
                new KakaoRegionClient.LegalRegion("1168010100", "역삼동");
        when(placeRepository.findByLatitudeAndLongitude(
                REQUEST.location().latitude(), REQUEST.location().longitude()))
                .thenReturn(Optional.empty());
        when(kakaoRegionClient.findLegalRegion(
                REQUEST.location().latitude(), REQUEST.location().longitude()))
                .thenReturn(expected);

        assertThat(preparationService.preparePlace(REQUEST)).isEqualTo(expected);

        InOrder order = inOrder(placeRepository, kakaoRegionClient);
        order.verify(placeRepository).findByLatitudeAndLongitude(
                REQUEST.location().latitude(), REQUEST.location().longitude());
        order.verify(kakaoRegionClient).findLegalRegion(
                REQUEST.location().latitude(), REQUEST.location().longitude());
    }

    @Test
    void propagatesKakaoFailureWithoutStartingRecordPersistence() {
        KakaoRegionLookupException failure = new KakaoRegionLookupException(
                KakaoRegionLookupException.Reason.API_ERROR);
        when(placeRepository.findByLatitudeAndLongitude(
                REQUEST.location().latitude(), REQUEST.location().longitude()))
                .thenReturn(Optional.empty());
        when(kakaoRegionClient.findLegalRegion(
                REQUEST.location().latitude(), REQUEST.location().longitude()))
                .thenThrow(failure);

        assertThatThrownBy(() -> preparationService.preparePlace(REQUEST))
                .isSameAs(failure);
    }

    @Test
    void preparesWeatherUsingRequestCoordinatesAndReturnsMappedData() {
        WeatherResponse response = new WeatherResponse("ok",
                new WeatherResponse.WeatherData(
                        OffsetDateTime.parse("2026-10-08T09:00:00+09:00"),
                        new BigDecimal("24.0"), WeatherCondition.CLEAR));
        when(weatherService.getWeather(eq(37.5000001), eq(127.0),
                any(OffsetDateTime.class))).thenReturn(response);

        PreparedWeather prepared = preparationService.prepareWeather(REQUEST);

        assertThat(prepared).isEqualTo(new PreparedWeather(
                new BigDecimal("24.0"), Record.WeatherCondition.CLEAR));
        verify(weatherService).getWeather(eq(37.5000001), eq(127.0),
                any(OffsetDateTime.class));
    }

    @Test
    void weatherApiExceptionProducesNoWeatherWithoutChangingFailurePolicy() {
        when(weatherService.getWeather(eq(37.5000001), eq(127.0),
                any(OffsetDateTime.class))).thenThrow(new WeatherApiException());

        assertThat(preparationService.prepareWeather(REQUEST))
                .isEqualTo(PreparedWeather.withoutWeather());
    }

    @Test
    void onlyWeatherApiExceptionIsConvertedToNoWeather() {
        IllegalStateException failure = new IllegalStateException("unexpected failure");
        when(weatherService.getWeather(eq(37.5000001), eq(127.0),
                any(OffsetDateTime.class))).thenThrow(failure);

        assertThatThrownBy(() -> preparationService.prepareWeather(REQUEST))
                .isSameAs(failure);
    }

    @Test
    void preparationAndOrchestrationRemainOutsideRecordTransaction() throws Exception {
        assertThat(RecordCreationPreparationService.class.getMethod(
                "preparePlace", RecordCreateRequest.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(RecordCreationPreparationService.class.getMethod(
                "prepareWeather", RecordCreateRequest.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(WeatherService.class.getMethod("getWeather", double.class,
                double.class, OffsetDateTime.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(WeatherCacheWriter.class.getMethod("saveDailyForecasts",
                GridCoordinate.class, LocalDate.class, ZonedDateTime.class,
                ZonedDateTime.class, List.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(WeatherCacheWriter.class.getMethod("saveMissingForecast",
                WeatherGrid.class, LocalDate.class, ZonedDateTime.class,
                ZonedDateTime.class, ForecastSlot.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(RecordCreationOrchestrator.class.getMethod(
                "createRecord", Long.class, RecordCreateRequest.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(RecordService.class.getMethod("createRecord", Long.class,
                RecordCreateRequest.class, KakaoRegionClient.LegalRegion.class,
                PreparedWeather.class).isAnnotationPresent(Transactional.class)).isTrue();
    }
}
