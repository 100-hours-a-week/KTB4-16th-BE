package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.recommendation.client.DailySongAiResponseException;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendationResult;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiException;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DailySongJobProcessingService {
    private final DailySongRecommendationContextService contextService;
    private final DailySongJobClaimRepository claims;
    private final RecommendationAiClient aiClient;
    private final DailySongJobResultService results;
    private final Clock clock;

    public DailySongJobProcessingService(DailySongRecommendationContextService contextService,
            DailySongJobClaimRepository claims, RecommendationAiClient aiClient,
            DailySongJobResultService results, Clock clock) {
        this.contextService = contextService;
        this.claims = claims;
        this.aiClient = aiClient;
        this.results = results;
        this.clock = clock;
    }

    public ProcessingOutcome process(Claim claim) {
        try {
            var context = contextService.build(claim);
            if (!claims.markAiCallStarted(claim, LocalDateTime.now(clock))) {
                return fail(claim, "CLAIM_NOT_AVAILABLE");
            }
            RecommendationResult result = aiClient.recommendOne(context);
            if (result != null && result.degraded()) {
                return fail(claim, "AI_DEGRADED");
            }
            if (result != null && result.tracks() != null && result.tracks().isEmpty()) {
                return fail(claim, "AI_EMPTY_RESULT");
            }
            if (result == null || result.tracks() == null
                    || result.tracks().size() != 1 || result.recommendReason() == null
                    || result.recommendReason().isBlank()
                    || result.recommendReason().getBytes(StandardCharsets.UTF_8).length > 65_535) {
                return fail(claim, "AI_INVALID_RESPONSE");
            }
            var track = result.tracks().getFirst();
            if (track == null || track.externalTrackId() == null || track.externalTrackId().isBlank()
                    || track.externalTrackId().length() > 22 || track.title() == null || track.title().isBlank()
                    || track.title().length() > 255 || track.artistName() == null || track.artistName().isBlank()
                    || track.artistName().length() > 255 || track.albumImageUrl() == null
                    || track.albumImageUrl().isBlank() || track.albumImageUrl().length() > 255
                    || track.externalUrl() == null || track.externalUrl().isBlank()
                    || track.externalUrl().length() > 255) {
                return fail(claim, "AI_INVALID_RESPONSE");
            }
            return switch (results.complete(claim, track, result.recommendReason())) {
                case COMPLETED -> ProcessingOutcome.COMPLETED;
                case CANCELLED -> ProcessingOutcome.CANCELLED;
                case NOT_APPLIED -> ProcessingOutcome.NOT_APPLIED;
            };
        } catch (DailySongAiResponseException exception) {
            return fail(claim, "AI_" + exception.failure().name());
        } catch (RecommendationAiException exception) {
            return fail(claim, classifyAiFailure(exception));
        } catch (WeatherApiException exception) {
            return fail(claim, "WEATHER_UNAVAILABLE");
        }
    }

    private ProcessingOutcome fail(Claim claim, String failureCode) {
        return claims.fail(claim, failureCode, LocalDateTime.now(clock)) == 1
                ? ProcessingOutcome.FAILURE_RECORDED : ProcessingOutcome.NOT_APPLIED;
    }

    public enum ProcessingOutcome {
        COMPLETED, FAILURE_RECORDED, CANCELLED, NOT_APPLIED
    }

    private String classifyAiFailure(RecommendationAiException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return "AI_TIMEOUT";
            }
            if (cause instanceof RestClientResponseException) {
                return "AI_HTTP_ERROR";
            }
        }
        return "AI_ERROR";
    }
}
