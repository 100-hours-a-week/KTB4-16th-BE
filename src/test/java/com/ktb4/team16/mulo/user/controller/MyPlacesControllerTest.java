package com.ktb4.team16.mulo.user.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.service.PlaceService;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MyPlacesControllerTest {
    private static final Long USER_ID = 1L;
    private static final BigDecimal SW_LAT = new BigDecimal("37.0");
    private static final BigDecimal SW_LNG = new BigDecimal("127.0");
    private static final BigDecimal NE_LAT = new BigDecimal("38.0");
    private static final BigDecimal NE_LNG = new BigDecimal("128.0");

    @Mock UserSignupService signupService;
    @Mock UserProfileService profileService;
    @Mock PlaceService placeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new UserController(signupService, profileService, placeService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsMyPlaceMarkersWithMessageAndData() throws Exception {
        when(placeService.getMyPlaceMarkersInBounds(
                USER_ID, SW_LAT, SW_LNG, NE_LAT, NE_LNG))
                .thenReturn(List.of(new MyPlaceMarkerResponse(
                        10L, "오산동", 3L,
                        new BigDecimal("37.2002"), new BigDecimal("127.0950"))));

        performValidRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 자물쇠 조회 성공"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].placeId").value(10))
                .andExpect(jsonPath("$.data[0].legalDongName").value("오산동"))
                .andExpect(jsonPath("$.data[0].myRecordsCount").value(3))
                .andExpect(jsonPath("$.data[0].latitude").value(37.2002))
                .andExpect(jsonPath("$.data[0].longitude").value(127.0950));
    }

    @Test
    void returnsEmptyDataWhenNoPlaceMarkersExist() throws Exception {
        when(placeService.getMyPlaceMarkersInBounds(
                USER_ID, SW_LAT, SW_LNG, NE_LAT, NE_LNG)).thenReturn(List.of());

        performValidRequest()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 자물쇠 조회 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingSwLatReturnsRequiredError() throws Exception {
        performRequest(null, "127.0", "38.0", "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("swLat"))
                .andExpect(jsonPath("$.errors[0].code").value("SW_LAT_REQUIRED"));
    }

    @Test
    void missingSwLngReturnsRequiredError() throws Exception {
        performRequest("37.0", null, "38.0", "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("swLng"))
                .andExpect(jsonPath("$.errors[0].code").value("SW_LNG_REQUIRED"));
    }

    @Test
    void missingNeLatReturnsRequiredError() throws Exception {
        performRequest("37.0", "127.0", null, "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("neLat"))
                .andExpect(jsonPath("$.errors[0].code").value("NE_LAT_REQUIRED"));
    }

    @Test
    void missingNeLngReturnsRequiredError() throws Exception {
        performRequest("37.0", "127.0", "38.0", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("neLng"))
                .andExpect(jsonPath("$.errors[0].code").value("NE_LNG_REQUIRED"));
    }

    @Test
    void missingMultipleCoordinatesReturnsEveryRequiredError() throws Exception {
        performRequest(null, null, null, "128.0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "SW_LAT_REQUIRED", "SW_LNG_REQUIRED", "NE_LAT_REQUIRED")));
    }

    @Test
    void swLatOutsideLatitudeRangeReturnsInvalidLatitude() throws Exception {
        assertFieldError("-91", "127.0", "38.0", "128.0",
                "swLat", "INVALID_LATITUDE");
    }

    @Test
    void neLatOutsideLatitudeRangeReturnsInvalidLatitude() throws Exception {
        assertFieldError("37.0", "127.0", "91", "128.0",
                "neLat", "INVALID_LATITUDE");
    }

    @Test
    void swLngOutsideLongitudeRangeReturnsInvalidLongitude() throws Exception {
        assertFieldError("37.0", "-181", "38.0", "128.0",
                "swLng", "INVALID_LONGITUDE");
    }

    @Test
    void neLngOutsideLongitudeRangeReturnsInvalidLongitude() throws Exception {
        assertFieldError("37.0", "127.0", "38.0", "181",
                "neLng", "INVALID_LONGITUDE");
    }

    @Test
    void multipleCoordinatesOutsideRangeReturnEveryFieldError() throws Exception {
        performRequest("-91", "-181", "91", "181")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[*].field", hasItems(
                        "swLat", "swLng", "neLat", "neLng")))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "INVALID_LATITUDE", "INVALID_LONGITUDE")));
    }

    @Test
    void equalLatitudesReturnInvalidMapBounds() throws Exception {
        assertInvalidMapBounds("37.0", "127.0", "37.0", "128.0");
    }

    @Test
    void descendingLatitudesReturnInvalidMapBounds() throws Exception {
        assertInvalidMapBounds("38.0", "127.0", "37.0", "128.0");
    }

    @Test
    void equalLongitudesReturnInvalidMapBounds() throws Exception {
        assertInvalidMapBounds("37.0", "127.0", "38.0", "127.0");
    }

    @Test
    void descendingLongitudesReturnInvalidMapBounds() throws Exception {
        assertInvalidMapBounds("37.0", "128.0", "38.0", "127.0");
    }

    private ResultActions performValidRequest() throws Exception {
        return performRequest("37.0", "127.0", "38.0", "128.0");
    }

    private ResultActions performRequest(
            String swLat, String swLng, String neLat, String neLng) throws Exception {
        var request = get("/api/users/me/places");
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

    private void assertFieldError(
            String swLat, String swLng, String neLat, String neLng,
            String field, String code) throws Exception {
        performRequest(swLat, swLng, neLat, neLng)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    private void assertInvalidMapBounds(
            String swLat, String swLng, String neLat, String neLng) throws Exception {
        performRequest(swLat, swLng, neLat, neLng)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MAP_BOUNDS"))
                .andExpect(jsonPath("$.message").value("지도 범위 정보가 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
