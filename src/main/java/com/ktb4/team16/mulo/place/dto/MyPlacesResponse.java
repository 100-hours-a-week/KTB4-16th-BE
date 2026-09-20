package com.ktb4.team16.mulo.place.dto;
import java.util.List;

public record MyPlacesResponse(
        String message,
        List<MyPlaceMarkerResponse> data
) {

}
