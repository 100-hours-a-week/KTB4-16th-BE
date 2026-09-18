package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ktb4.team16.mulo.record.dto.RecordMarkerResponseDto;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordService {
    
    private final RecordRepository recordRepository;

    public List<RecordMarkerResponseDto> getMyMarkers(
        Long userId,
        BigDecimal swLat,
        BigDecimal swLng,
        BigDecimal neLat,
        BigDecimal neLng
) {
    return recordRepository.findMarkersInBounds(
        userId,
        swLat,
        swLng,
        neLat,
        neLng
    );
}

}