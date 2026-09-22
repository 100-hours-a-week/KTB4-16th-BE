package com.ktb4.team16.mulo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.dto.request.MapBoundsQuery;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaceServiceTests {

    private static final BigDecimal SW_LAT = new BigDecimal("37.0000000");
    private static final BigDecimal SW_LNG = new BigDecimal("127.0000000");
    private static final BigDecimal NE_LAT = new BigDecimal("38.0000000");
    private static final BigDecimal NE_LNG = new BigDecimal("128.0000000");
    private static final MapBoundsQuery BOUNDS =
            new MapBoundsQuery(SW_LAT, SW_LNG, NE_LAT, NE_LNG);

    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final PlaceService placeService = new PlaceService(recordRepository);

    @Test
    void returnsAllRecordMarkersFromRepository() {
        AllRecordMarkerResponse marker = new AllRecordMarkerResponse(
                1L, 3L, "1168010100", "역삼동", new BigDecimal("37.5000000"),
                new BigDecimal("127.5000000"));
        List<AllRecordMarkerResponse> markers = List.of(marker);
        when(recordRepository.findAllRecordMarkersInBounds(
                eq(SW_LAT), eq(SW_LNG), eq(NE_LAT), eq(NE_LNG), any(LocalDateTime.class)))
                .thenReturn(markers);

        LocalDateTime before = LocalDateTime.now().minusDays(7);
        List<AllRecordMarkerResponse> result = placeService.getAllRecordMarkersInBounds(BOUNDS);
        LocalDateTime after = LocalDateTime.now().minusDays(7);

        assertThat(result).containsExactly(marker);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recordRepository).findAllRecordMarkersInBounds(
                eq(SW_LAT), eq(SW_LNG), eq(NE_LAT), eq(NE_LNG), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void returnsEmptyListWhenRepositoryFindsNoPlaces() {
        when(recordRepository.findAllRecordMarkersInBounds(
                eq(SW_LAT), eq(SW_LNG), eq(NE_LAT), eq(NE_LNG), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<AllRecordMarkerResponse> result = placeService.getAllRecordMarkersInBounds(BOUNDS);

        assertThat(result).isEmpty();
        verify(recordRepository).findAllRecordMarkersInBounds(
                eq(SW_LAT), eq(SW_LNG), eq(NE_LAT), eq(NE_LNG), any(LocalDateTime.class));
    }
}
