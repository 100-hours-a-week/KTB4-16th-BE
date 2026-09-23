package com.ktb4.team16.mulo.place.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.place.dto.request.MapBoundsQuery;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.service.PlaceService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AllRecordMarkersControllerTest {

    private static final MapBoundsQuery BOUNDS = new MapBoundsQuery(
            new BigDecimal("37.0"), new BigDecimal("127.0"),
            new BigDecimal("38.0"), new BigDecimal("128.0"));

    @Mock
    private PlaceService placeService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PlaceController(placeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsMarkersWithMessageAndData() throws Exception {
        AllRecordMarkerResponse marker = new AllRecordMarkerResponse(
                10L, 3L, "4159012700", "오산동", new BigDecimal("37.2002"),
                new BigDecimal("127.0950"));
        when(placeService.getAllRecordMarkersInBounds(eq(BOUNDS))).thenReturn(List.of(marker));

        performRequest("37.0", "127.0", "38.0", "128.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("전체 자물쇠 마커 조회 성공"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].placeId").value(10))
                .andExpect(jsonPath("$.data[0].recordsCount").value(3))
                .andExpect(jsonPath("$.data[0].recordCount").doesNotExist())
                .andExpect(jsonPath("$.data[0].legalDongCode").value("4159012700"))
                .andExpect(jsonPath("$.data[0].legalDongName").value("오산동"))
                .andExpect(jsonPath("$.data[0].latitude").value(37.2002))
                .andExpect(jsonPath("$.data[0].longitude").value(127.0950));

        verify(placeService).getAllRecordMarkersInBounds(BOUNDS);
    }

    @Test
    void preservesNullLegalDongFields() throws Exception {
        AllRecordMarkerResponse marker = new AllRecordMarkerResponse(
                10L, 1L, null, null, new BigDecimal("37.2002"),
                new BigDecimal("127.0950"));
        when(placeService.getAllRecordMarkersInBounds(eq(BOUNDS))).thenReturn(List.of(marker));

        performRequest("37.0", "127.0", "38.0", "128.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].legalDongCode").hasJsonPath())
                .andExpect(jsonPath("$.data[0].legalDongCode").value(nullValue()))
                .andExpect(jsonPath("$.data[0].legalDongName").hasJsonPath())
                .andExpect(jsonPath("$.data[0].legalDongName").value(nullValue()));
    }

    @Test
    void returnsEmptyDataWhenNoMarkersExist() throws Exception {
        when(placeService.getAllRecordMarkersInBounds(eq(BOUNDS))).thenReturn(List.of());

        performRequest("37.0", "127.0", "38.0", "128.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("전체 자물쇠 마커 조회 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingCoordinateReturnsRequiredError() throws Exception {
        performRequest(null, "127.0", "38.0", "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("swLat"))
                .andExpect(jsonPath("$.errors[0].code").value("SW_LAT_REQUIRED"));
    }

    @Test
    void latitudeAndLongitudeOutsideRangeReturnValidationErrors() throws Exception {
        performRequest("-91", "-181", "38.0", "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "INVALID_LATITUDE", "INVALID_LONGITUDE")));
    }

    @Test
    void reversedBoundsReturnInvalidMapBounds() throws Exception {
        performRequest("38.0", "127.0", "37.0", "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MAP_BOUNDS"));
    }

    private ResultActions performRequest(
            String swLat, String swLng, String neLat, String neLng) throws Exception {
        var request = get("/api/places/popular");
        if (swLat != null) {
            request.param("swLat", swLat);
        }
        if (swLng != null) {
            request.param("swLng", swLng);
        }
        if (neLat != null) {
            request.param("neLat", neLat);
        }
        if (neLng != null) {
            request.param("neLng", neLng);
        }
        return mvc.perform(request);
    }
}
