package com.ktb4.team16.mulo.place.controller;

import com.ktb4.team16.mulo.place.dto.request.MapBoundsQuery;
import com.ktb4.team16.mulo.place.dto.request.PopularTracksSearchRequest;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkersResponse;
import com.ktb4.team16.mulo.place.dto.response.PopularTracksResponse;
import jakarta.validation.Valid;
import com.ktb4.team16.mulo.place.exception.InvalidMapBoundsException;
import com.ktb4.team16.mulo.place.message.PlaceMessage;
import com.ktb4.team16.mulo.place.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceService placeService;

    @GetMapping("/popular")
    public AllRecordMarkersResponse getAllRecordMarkers(
            @Valid @ModelAttribute MapBoundsQuery query
    ){
        if (!query.hasValidBounds()) {
            throw new InvalidMapBoundsException();
        }

        List<AllRecordMarkerResponse> markers = placeService.getAllRecordMarkersInBounds(query);

        return new AllRecordMarkersResponse(
                PlaceMessage.ALL_RECORD_MARKERS_RETRIEVED.message(),
                markers
        );
    }

    @PostMapping("/popular-tracks/search")
    public PopularTracksResponse searchPopularTracks(
            @Valid @RequestBody PopularTracksSearchRequest request
    ) {
        return new PopularTracksResponse(
                PlaceMessage.POPULAR_TRACKS_RETRIEVED.message(),
                placeService.getPopularTracks(request.placeIds())
        );
    }
}
