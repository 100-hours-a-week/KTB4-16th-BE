package com.ktb4.team16.mulo.place.service;

import com.ktb4.team16.mulo.place.dto.PopularPlaceMarkerResponseDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final RecordRepository recordRepository;

    @Transactional(readOnly = true)
    public List<PopularPlaceMarkerResponseDto> getPopularPlaceMarkersInBounds(
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng
    ) {
        return recordRepository.findPopularPlacesInBounds(swLat, swLng, neLat, neLng);
    }
}
