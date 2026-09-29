package com.ktb4.team16.mulo.record.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException;
import com.ktb4.team16.mulo.record.service.RecordService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecordCreateLocationControllerTest {
    @Mock
    private RecordService recordService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecordController(recordService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"91, 127, latitude, INVALID_LATITUDE", "37, 181, longitude, INVALID_LONGITUDE"})
    void rejectsOutOfRangeCoordinatesBeforeService(
            String latitude, String longitude, String field, String code
    ) throws Exception {
        mvc.perform(post("/api/records").contentType(MediaType.APPLICATION_JSON)
                        .content(body(latitude, longitude)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("location." + field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
        verifyNoInteractions(recordService);
    }

    @ParameterizedTest
    @CsvSource({
            "LEGAL_REGION_NOT_FOUND, 400, UNSUPPORTED_RECORD_LOCATION",
            "API_ERROR, 502, LOCATION_SERVICE_ERROR",
            "INVALID_RESPONSE, 502, LOCATION_SERVICE_ERROR"
    })
    void distinguishesUnsupportedLocationFromExternalServiceFailure(
            KakaoRegionLookupException.Reason reason, int httpStatus, String code
    ) throws Exception {
        when(recordService.createRecord(eq(35L), any())).thenThrow(new KakaoRegionLookupException(reason));
        mvc.perform(post("/api/records").contentType(MediaType.APPLICATION_JSON)
                        .content(body("37.5", "127")))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String body(String latitude, String longitude) {
        return """
                {"location":{"latitude":%s,"longitude":%s},
                 "music":{"externalTrackId":"track-id","title":"title","artistName":"artist",
                 "albumImageUrl":"album-image","externalUrl":"external-url"},
                 "moodScore":0,"uploadId":11}
                """.formatted(latitude, longitude);
    }
}
