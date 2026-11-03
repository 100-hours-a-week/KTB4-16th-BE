package com.ktb4.team16.mulo.place.service;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.request.MapBoundsQuery;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;
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

    /** 최근 7일의 전체 곡 집계에서 순위와 전체 기록 건수를 함께 구성한다. */
    @Transactional(readOnly = true)
    public PopularTracksResponseDto getPopularTracks(List<Long> placeIds) {
        LocalDateTime createdAtFrom = LocalDateTime.now().minusDays(7);
        List<PopularTrackAggregateDto> aggregates = recordRepository.findPopularTrackAggregates(
                placeIds, createdAtFrom);
        // LIMIT 없는 전체 집계이므로 각 기록이 포함된 그룹의 건수를 합해 중복 COUNT를 생략한다.
        long recordCount = aggregates.stream().mapToLong(PopularTrackAggregateDto::count).sum();

        List<PopularTracksResponseDto.Music> music = new java.util.ArrayList<>(aggregates.size());
        for (int index = 0; index < aggregates.size(); index++) {
            PopularTrackAggregateDto aggregate = aggregates.get(index);
            music.add(new PopularTracksResponseDto.Music(
                    index + 1,
                    aggregate.musicTrackId(),
                    aggregate.title(),
                    aggregate.artistName(),
                    aggregate.count(),
                    aggregate.externalUrl()
            ));
        }

        return new PopularTracksResponseDto(recordCount, music);
    }
}
