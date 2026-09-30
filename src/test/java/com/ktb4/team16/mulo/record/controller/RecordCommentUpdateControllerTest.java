package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.service.RecordCommentUpdateOrchestrator;
import com.ktb4.team16.mulo.record.service.RecordCreationOrchestrator;
import com.ktb4.team16.mulo.record.service.RecordDeletionOrchestrator;
import com.ktb4.team16.mulo.record.service.RecordService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecordCommentUpdateControllerTest {
    @Mock
    private RecordService recordService;
    @Mock
    private RecordCreationOrchestrator recordCreationOrchestrator;
    @Mock
    private RecordCommentUpdateOrchestrator recordCommentUpdateOrchestrator;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecordController(recordService, recordCreationOrchestrator,
                                recordCommentUpdateOrchestrator,
                                mock(RecordDeletionOrchestrator.class)))
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
    void updatesCommentForAuthenticatedUser() throws Exception {
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, 125L, "수정된 코멘트"))
                .thenReturn(new RecordCommentUpdateResponse(125L, "수정된 코멘트"));

        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"수정된 코멘트\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("코멘트 수정 성공"))
                .andExpect(jsonPath("$.data.recordId").value(125))
                .andExpect(jsonPath("$.data.comment").value("수정된 코멘트"));

        verify(recordCommentUpdateOrchestrator).updateRecordComment(35L, 125L, "수정된 코멘트");
    }

    @Test
    void allowsNullCommentToDeleteComment() throws Exception {
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, 125L, null))
                .thenReturn(new RecordCommentUpdateResponse(125L, null));

        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comment").value(org.hamcrest.Matchers.nullValue()));

        verify(recordCommentUpdateOrchestrator).updateRecordComment(35L, 125L, null);
    }

    @Test
    void preservesEmptyComment() throws Exception {
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, 125L, ""))
                .thenReturn(new RecordCommentUpdateResponse(125L, ""));

        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comment").value(""));

        verify(recordCommentUpdateOrchestrator).updateRecordComment(35L, 125L, "");
    }

    @Test
    void rejectsMissingCommentFieldBeforeCallingService() throws Exception {
        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("comment"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(recordService);
        verifyNoInteractions(recordCommentUpdateOrchestrator);
    }

    @Test
    void acceptsCommentWithEightyCharacters() throws Exception {
        String comment = "a".repeat(80);
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, 125L, comment))
                .thenReturn(new RecordCommentUpdateResponse(125L, comment));

        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + comment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comment").value(comment));
    }

    @Test
    void rejectsCommentLongerThanEightyCharacters() throws Exception {
        mvc.perform(patch("/api/records/125/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "a".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("comment"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMENT_TOO_LONG"));

        verifyNoInteractions(recordService);
        verifyNoInteractions(recordCommentUpdateOrchestrator);
    }

    @Test
    void returnsExistingInvalidRecordIdResponse() throws Exception {
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, 0L, "comment"))
                .thenThrow(new InvalidRecordIdException());
        when(recordCommentUpdateOrchestrator.updateRecordComment(35L, -1L, "comment"))
                .thenThrow(new InvalidRecordIdException());

        mvc.perform(patch("/api/records/0/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"comment\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));

        mvc.perform(patch("/api/records/-1/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"comment\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("recordId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RECORD_ID"));
    }
}
