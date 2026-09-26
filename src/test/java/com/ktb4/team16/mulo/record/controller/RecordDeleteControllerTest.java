package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.service.RecordService;
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
class RecordDeleteControllerTest {
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
    void deletesRecordForAuthenticatedUser() throws Exception {
        mvc.perform(delete("/api/records/125"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("자물쇠가 삭제되었습니다."));

        verify(recordService).deleteRecord(35L, 125L);
    }

    @Test
    void returnsExistingInvalidRecordIdResponse() throws Exception {
        doThrow(new InvalidRecordIdException()).when(recordService).deleteRecord(35L, 0L);
        doThrow(new InvalidRecordIdException()).when(recordService).deleteRecord(35L, -1L);

        mvc.perform(delete("/api/records/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));

        mvc.perform(delete("/api/records/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));
    }
}
