package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.dto.response.RecordDetailData;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.service.RecordService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecordDetailControllerTest {
    @Mock
    private RecordService recordService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecordController(recordService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsRecordDetailForAuthenticatedUser() throws Exception {
        when(recordService.getRecordDetail(35L, 10L)).thenReturn(new RecordDetailData(
                10L,
                35L,
                new RecordDetailData.Place(
                        20L,
                        "테스트동",
                        new BigDecimal("37.5000000"),
                        new BigDecimal("127.0000000"),
                        "1111010100"),
                new RecordDetailData.Music(
                        30L,
                        "title",
                        "artist",
                        "album-image",
                        "external-url"),
                Record.WeatherCondition.CLEAR,
                new BigDecimal("20.5"),
                (byte) 10,
                "comment",
                "https://signed.example/photo",
                LocalDateTime.of(2026, 9, 26, 12, 0)));

        mvc.perform(get("/api/records/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("자물쇠 상세 조회 성공"))
                .andExpect(jsonPath("$.data.recordId").value(10))
                .andExpect(jsonPath("$.data.userId").value(35))
                .andExpect(jsonPath("$.data.place.placeId").value(20))
                .andExpect(jsonPath("$.data.music.musicTrackId").value(30))
                .andExpect(jsonPath("$.data.photoUrl").value("https://signed.example/photo"));

        verify(recordService).getRecordDetail(35L, 10L);
    }

    @Test
    void returnsInvalidInputValueFieldErrorForNonPositiveRecordId() throws Exception {
        when(recordService.getRecordDetail(35L, 0L)).thenThrow(new InvalidRecordIdException());
        when(recordService.getRecordDetail(35L, -1L)).thenThrow(new InvalidRecordIdException());

        mvc.perform(get("/api/records/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));

        mvc.perform(get("/api/records/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));
    }
}
