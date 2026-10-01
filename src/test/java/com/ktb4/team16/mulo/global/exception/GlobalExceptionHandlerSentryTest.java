package com.ktb4.team16.mulo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

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
}
