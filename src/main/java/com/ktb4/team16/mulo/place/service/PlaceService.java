package com.ktb4.team16.mulo.place.service;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.request.MapBoundsQuery;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.exception.InvalidMapBoundsException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final RecordRepository recordRepository;

    @Transactional(readOnly = true)
    public List<AllRecordMarkerResponse> getAllRecordMarkersInBounds(
            MapBoundsQuery query
    ) {
        if (!query.hasValidBounds()) {
            throw new InvalidMapBoundsException();
        }

        LocalDateTime createdAtFrom = LocalDateTime.now().minusDays(7);

        return recordRepository.findAllRecordMarkersInBounds(
                query.swLat(), query.swLng(), query.neLat(), query.neLng(), createdAtFrom);
    }

    @Transactional(readOnly = true)
    public List<MyPlaceMarkerResponse> getMyPlaceMarkersInBounds(
            Long userId,
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng
    ) {
        return recordRepository.findMyPlaceMarkersInBounds(
                userId, swLat, swLng, neLat, neLng);
    }
}
