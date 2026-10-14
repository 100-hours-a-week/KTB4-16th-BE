package com.ktb4.team16.mulo.recorddraft.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.recorddraft.service.RecordDraftService;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RecordDraftControllerTest {
    @Mock private RecordDraftService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecordDraftController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(17L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsNoContentWhenDraftIsMissingOrExpired() throws Exception {
        when(service.findActiveRecordDraftForOwner(17L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/users/me/record-draft"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsDraftAndExpirationWithoutExposingAnotherUserId() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 14, 9, 0);
        Upload upload = org.mockito.Mockito.mock(Upload.class);
        when(upload.getUploadId()).thenReturn(88L);
        RecordDraft draft = RecordDraft.create(User.signup("a@b.test", "hash", "nick"),
                null, null, null, null, "track", "title", "artist", "album", "url",
                (byte) 0, "note", upload, updatedAt);
        when(service.findActiveRecordDraftForOwner(17L)).thenReturn(Optional.of(draft));
        when(service.createPhotoUrl(17L, draft)).thenReturn("signed-url");

        mvc.perform(get("/api/users/me/record-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("임시저장 조회 성공"))
                .andExpect(jsonPath("$.data.music.externalTrackId").value("track"))
                .andExpect(jsonPath("$.data.moodScore").value(0))
                .andExpect(jsonPath("$.data.uploadId").value(88))
                .andExpect(jsonPath("$.data.photoUrl").value("signed-url"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-28T09:00:00"));
    }

    @Test
    void savesFullSnapshotWithAuthenticatedOwner() throws Exception {
        mvc.perform(put("/api/users/me/record-draft")
                        .contentType("application/json")
                        .content("""
                                {"location":{"latitude":37.5,"longitude":127.0},
                                 "moodScore":12,"comment":"note","uploadId":88}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("임시저장 저장 성공"));

        ArgumentCaptor<com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest>
                captor = ArgumentCaptor.forClass(
                com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest.class);
        verify(service).upsertRecordDraft(org.mockito.ArgumentMatchers.eq(17L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().uploadId()).isEqualTo(88L);
    }

    @Test
    void validatesSnapshotBeforeCallingService() throws Exception {
        mvc.perform(put("/api/users/me/record-draft")
                        .contentType("application/json")
                        .content("{\"moodScore\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verify(service, org.mockito.Mockito.never()).upsertRecordDraft(any(), any());
    }

    @Test
    void deleteIsNoContentAndCanBeRepeated() throws Exception {
        mvc.perform(delete("/api/users/me/record-draft"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/me/record-draft"))
                .andExpect(status().isNoContent());

        verify(service, times(2)).deleteRecordDraft(17L);
    }
}
