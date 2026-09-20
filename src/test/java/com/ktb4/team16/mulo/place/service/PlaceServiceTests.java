package com.ktb4.team16.mulo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.dto.PopularPlaceMarkerResponse;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceServiceTests {

    private static final BigDecimal SW_LAT = new BigDecimal("37.0000000");
    private static final BigDecimal SW_LNG = new BigDecimal("127.0000000");
    private static final BigDecimal NE_LAT = new BigDecimal("38.0000000");
    private static final BigDecimal NE_LNG = new BigDecimal("128.0000000");
    private static final LocalDateTime CREATED_AT_FROM = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final PlaceService placeService = new PlaceService(recordRepository);

    @Test
    void returnsPopularPlaceMarkersFromRepository() {
        PopularPlaceMarkerResponse marker = new PopularPlaceMarkerResponse(
                1L, 3L, new BigDecimal("37.5000000"), new BigDecimal("127.5000000"));
        List<PopularPlaceMarkerResponse> markers = List.of(marker);
        when(recordRepository.findPopularPlacesInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM))
                .thenReturn(markers);

        List<PopularPlaceMarkerResponse> result = placeService.getPopularPlaceMarkersInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM);

        assertThat(result).containsExactly(marker);
        verify(recordRepository).findPopularPlacesInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM);
    }

    @Test
    void returnsEmptyListWhenRepositoryFindsNoPlaces() {
        when(recordRepository.findPopularPlacesInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM))
                .thenReturn(List.of());

        List<PopularPlaceMarkerResponse> result = placeService.getPopularPlaceMarkersInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM);

        assertThat(result).isEmpty();
        verify(recordRepository).findPopularPlacesInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, CREATED_AT_FROM);
    }
}
