package com.ktb4.team16.mulo.dailysong.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobResultService.CompletionOutcome;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobProcessingService.ProcessingOutcome;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendationContext;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendationResult;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiException;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

class DailySongJobProcessingServiceTest {
    private final DailySongRecommendationContextService contexts = mock(DailySongRecommendationContextService.class);
    private final DailySongJobClaimRepository claims = mock(DailySongJobClaimRepository.class);
    private final RecommendationAiClient ai = mock(RecommendationAiClient.class);
    private final DailySongJobResultService results = mock(DailySongJobResultService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.ofHours(9));
    private final DailySongJobProcessingService service = new DailySongJobProcessingService(
            contexts, claims, ai, results, clock);
    private final Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10),
            LocalDateTime.of(2026, 11, 10, 0, 30), "token");
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 9, 0);
    private final RecommendationContext context = new RecommendationContext(13L, WeatherCondition.CLEAR,
            new BigDecimal("20"), OffsetDateTime.parse("2026-11-10T00:30:00+09:00"),
            List.of(), null, List.of());

    @BeforeEach
    void configure() {
        when(contexts.build(claim)).thenReturn(context);
        when(claims.markAiCallStarted(claim, now)).thenReturn(true);
    }

    @Test
    void storesOneValidRecommendation() {
        var track = new RecommendationAiClient.RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "테스트 곡",
                "테스트 가수", "https://example.test/album", "https://example.test/track");
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(track), false));
        when(results.complete(claim, track, "추천 이유")).thenReturn(CompletionOutcome.COMPLETED);

        assertThat(service.process(claim)).isEqualTo(ProcessingOutcome.COMPLETED);

        verify(results).complete(claim, track, "추천 이유");
    }

    @Test
    void completionNotAppliedRemainsUnconfirmed() {
        var track = new RecommendationAiClient.RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "테스트 곡",
                "테스트 가수", "https://example.test/album", "https://example.test/track");
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(track), false));
        when(results.complete(claim, track, "추천 이유")).thenReturn(CompletionOutcome.NOT_APPLIED);

        assertThat(service.process(claim)).isEqualTo(ProcessingOutcome.NOT_APPLIED);
    }

    @Test
    void withdrawnUserAfterAiCallIsReportedAsCancelled() {
        var track = new RecommendationAiClient.RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "테스트 곡",
                "테스트 가수", "https://example.test/album", "https://example.test/track");
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(track), false));
        when(results.complete(claim, track, "추천 이유")).thenReturn(CompletionOutcome.CANCELLED);

        assertThat(service.process(claim)).isEqualTo(ProcessingOutcome.CANCELLED);
    }

    @Test
    void emptyResultIsFailedWithoutSavingTrack() {
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(), false));
        when(claims.fail(claim, "AI_EMPTY_RESULT", now)).thenReturn(1);

        assertThat(service.process(claim)).isEqualTo(ProcessingOutcome.FAILURE_RECORDED);

        verify(claims).fail(claim, "AI_EMPTY_RESULT", now);
        verifyNoInteractions(results);
    }

    @Test
    void failedUpdateWithoutChangedRowRemainsUnconfirmed() {
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(), false));

        assertThat(service.process(claim)).isEqualTo(ProcessingOutcome.NOT_APPLIED);
        verify(claims).fail(claim, "AI_EMPTY_RESULT", now);
    }

    @Test
    void degradedResultIsFailedWithoutSavingTrack() {
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(), true));

        service.process(claim);

        verify(claims).fail(claim, "AI_DEGRADED", now);
        verifyNoInteractions(results);
    }

    @Test
    void trackThatCannotFitMusicTableIsRejectedBeforeDbWrite() {
        var track = new RecommendationAiClient.RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "곡".repeat(256),
                "테스트 가수", "https://example.test/album", "https://example.test/track");
        when(ai.recommendOne(context)).thenReturn(new RecommendationResult("추천 이유", List.of(track), false));

        service.process(claim);

        verify(claims).fail(claim, "AI_INVALID_RESPONSE", now);
        verifyNoInteractions(results);
    }

    @Test
    void timeoutIsRecordedSeparately() {
        when(ai.recommendOne(context)).thenThrow(new RecommendationAiException(
                new HttpTimeoutException("AI 응답 시간이 초과됐습니다.")));

        service.process(claim);

        verify(claims).fail(claim, "AI_TIMEOUT", now);
    }

    @Test
    void httpFailureIsRecordedSeparately() {
        when(ai.recommendOne(context)).thenThrow(new RecommendationAiException(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)));

        service.process(claim);

        verify(claims).fail(claim, "AI_HTTP_ERROR", now);
    }

    @Test
    void lostClaimDoesNotCallAi() {
        when(claims.markAiCallStarted(claim, now)).thenReturn(false);

        service.process(claim);

        verifyNoInteractions(ai, results);
        verify(claims).fail(claim, "CLAIM_NOT_AVAILABLE", now);
    }

    @Test
    void databaseFailureIsNotMisclassifiedAsAiFailure() {
        when(contexts.build(claim)).thenThrow(new DataAccessResourceFailureException("DB 연결이 실패했습니다."));

        assertThatThrownBy(() -> service.process(claim))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(ai, results);
    }
}
