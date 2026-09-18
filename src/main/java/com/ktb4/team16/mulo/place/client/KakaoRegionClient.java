package com.ktb4.team16.mulo.place.client;

import com.ktb4.team16.mulo.place.client.dto.KakaoRegionResponse;
import com.ktb4.team16.mulo.place.client.dto.KakaoRegionResponse.RegionDocument;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoRegionClient {

    private static final String REGION_PATH = "/v2/local/geo/coord2regioncode.json";

    private final RestClient restClient;

    @Autowired
    public KakaoRegionClient(@Value("${kakao.rest-api-key}") String restApiKey) {
        this(RestClient.builder(), restApiKey);
    }

    KakaoRegionClient(RestClient.Builder builder, String restApiKey) {
        this.restClient = builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .build();
    }

    public String findLegalRegionName(BigDecimal latitude, BigDecimal longitude) {
        KakaoRegionResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(REGION_PATH)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .retrieve()
                    .body(KakaoRegionResponse.class);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new KakaoRegionLookupException(KakaoRegionLookupException.Reason.API_ERROR);
        } catch (RestClientException exception) {
            throw new KakaoRegionLookupException(KakaoRegionLookupException.Reason.INVALID_RESPONSE);
        }

        if (response == null || response.documents() == null
                || response.documents().stream().anyMatch(document -> document == null)) {
            throw new KakaoRegionLookupException(KakaoRegionLookupException.Reason.INVALID_RESPONSE);
        }

        RegionDocument legalRegion = response.documents().stream()
                .filter(document -> "B".equals(document.region_type()))
                .findFirst()
                .orElseThrow(() -> new KakaoRegionLookupException(
                        KakaoRegionLookupException.Reason.LEGAL_REGION_NOT_FOUND));

        if (legalRegion.region_3depth_name() == null || legalRegion.region_3depth_name().isBlank()) {
            throw new KakaoRegionLookupException(KakaoRegionLookupException.Reason.LEGAL_REGION_NOT_FOUND);
        }
        return legalRegion.region_3depth_name();
    }
}
