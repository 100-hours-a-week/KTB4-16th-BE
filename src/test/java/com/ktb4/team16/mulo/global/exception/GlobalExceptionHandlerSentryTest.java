package com.ktb4.team16.mulo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

class GlobalExceptionHandlerSentryTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void capturesUnexpected500Exception() {
        Exception exception = new IllegalStateException("unexpected failure");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            var response = handler.handleUnexpectedException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            sentry.verify(() -> Sentry.captureException(exception));
            sentry.verifyNoMoreInteractions();
        }
    }

    @Test
    void doesNotCaptureHandled4xxException() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            var response = handler.handleInvalidRefreshToken(
                    new com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException());

            assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.status());
            sentry.verifyNoInteractions();
        }
    }

    /** 본문 파싱 실패를 400으로 처리할 때 Sentry에 장애를 전송하지 않는다. */
    @Test
    void doesNotCaptureUnreadableRequestBody() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            var response = handler.handleInvalidParameter(mock(HttpMessageNotReadableException.class));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            sentry.verifyNoInteractions();
        }
    }

    /** 미지원 요청 미디어 타입을 415로 처리할 때 Sentry에 장애를 전송하지 않는다. */
    @Test
    void doesNotCaptureUnsupportedMediaType() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            var response = handler.handleUnsupportedMediaType(mock(HttpMediaTypeNotSupportedException.class));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            sentry.verifyNoInteractions();
        }
    }
}
