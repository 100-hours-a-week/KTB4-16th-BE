package com.ktb4.team16.mulo.upload.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.upload.dto.response.UploadResponse;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException;
import com.ktb4.team16.mulo.upload.exception.HeicConversionException.Reason;
import com.ktb4.team16.mulo.upload.exception.UploadNotFoundException;
import com.ktb4.team16.mulo.upload.service.UploadService;
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
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {
    @Mock
    private UploadService uploadService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UploadController(uploadService))
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
    void returnsCreatedWithUploadId() throws Exception {
        when(uploadService.upload(org.mockito.ArgumentMatchers.eq(35L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UploadResponse("사진 업로드 성공", new UploadResponse.Data(123L)));

        mvc.perform(multipart("/api/uploads")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg",
                                new byte[]{1})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("사진 업로드 성공"))
                .andExpect(jsonPath("$.data.uploadId").value(123));
    }

    @Test
    void returnsSignedUrlForAuthenticatedOwner() throws Exception {
        when(uploadService.createReadSignedUrl(35L, 123L))
                .thenReturn("https://signed.example/photo");

        mvc.perform(get("/api/uploads/123/signed-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사진 조회 URL 발급 성공"))
                .andExpect(jsonPath("$.data.signedUrl")
                        .value("https://signed.example/photo"));

        verify(uploadService).createReadSignedUrl(35L, 123L);
    }

    @Test
    void returnsNotFoundWhenUploadIsNotValidForOwner() throws Exception {
        when(uploadService.createReadSignedUrl(35L, 123L))
                .thenThrow(new UploadNotFoundException());

        mvc.perform(get("/api/uploads/123/signed-url"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
    }

    @Test
    void returnsBadRequestWhenPhotoIsMissing() throws Exception {
        mvc.perform(multipart("/api/uploads"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photo"));
    }

    @Test
    void returnsUnsupportedMediaTypeWhenHeicCannotBeDecoded() throws Exception {
        doThrow(new HeicConversionException(Reason.HEIF_CONVERT_FAILED))
                .when(uploadService).upload(
                        org.mockito.ArgumentMatchers.eq(35L),
                        org.mockito.ArgumentMatchers.any());

        mvc.perform(multipart("/api/uploads").file(heic()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_FORMAT"));
    }

    @Test
    void returnsPayloadTooLargeWhenHeicExceedsPixelLimit() throws Exception {
        doThrow(new HeicConversionException(Reason.PIXEL_LIMIT_EXCEEDED))
                .when(uploadService).upload(
                        org.mockito.ArgumentMatchers.eq(35L),
                        org.mockito.ArgumentMatchers.any());

        mvc.perform(multipart("/api/uploads").file(heic()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_PIXEL_COUNT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("사진 해상도는 2,500만 픽셀 이하여야 합니다."));
    }

    @Test
    void hidesNativeInfrastructureFailureBehindInternalServerError() throws Exception {
        doThrow(new HeicConversionException(Reason.JPEGTRAN_UNAVAILABLE))
                .when(uploadService).upload(
                        org.mockito.ArgumentMatchers.eq(35L),
                        org.mockito.ArgumentMatchers.any());

        mvc.perform(multipart("/api/uploads").file(heic()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private MockMultipartFile heic() {
        return new MockMultipartFile("photo", "photo.heic", "image/heic", new byte[]{1});
    }
}
