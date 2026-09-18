package com.ktb4.team16.mulo.place.client.dto;

import java.util.List;

public record KakaoRegionResponse(List<RegionDocument> documents) {

    public record RegionDocument(
            String region_type,
            String code,
            String address_name,
            String region_1depth_name,
            String region_2depth_name,
            String region_3depth_name,
            String region_4depth_name
    ) {
    }
}
