package com.ktb4.team16.mulo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerMusicTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsRetryAfterForMusicSearchRateLimit() {
        var response = handler.handleMusicSearchRateLimited(
                new MusicSearchRateLimitedException(Duration.ofMillis(6_001)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.MUSIC_SEARCH_RATE_LIMITED);
    }

    @Test
    void hidesProviderCauseBehindStableUnavailableError() {
        var response = handler.handleMusicProviderUnavailable(
                new MusicProviderUnavailableException(new RuntimeException("secret upstream body")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.MUSIC_PROVIDER_UNAVAILABLE);
        assertThat(response.getBody().message()).doesNotContain("secret upstream body");
    }
}
