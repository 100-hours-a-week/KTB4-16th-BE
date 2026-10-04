package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiGateway;
import com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiClient.PhotoRecommendationAiResponse;
import com.ktb4.team16.mulo.recommendation.dto.response.PhotoRecommendationResponse;
import com.ktb4.team16.mulo.upload.service.UploadService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PhotoRecommendationService {
    private static final int MAX_TRACKS = 3;

    private final UploadService uploadService;
    private final PhotoRecommendationAiGateway aiClient;

    public List<PhotoRecommendationResponse.Track> recommend(Long userId, Long uploadId) {
        String signedUrl = uploadService.createReadSignedUrl(userId, uploadId);
        PhotoRecommendationAiResponse response = aiClient.recommend(signedUrl);
        if (response.degraded()) {
            throw new com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException();
        }
        return response.tracks().stream()
                .limit(MAX_TRACKS)
                .peek(PhotoRecommendationService::validateTrack)
                .map(track -> new PhotoRecommendationResponse.Track(
                        track.externalTrackId(), track.title(), track.artistName(),
                        track.albumImageUrl(), track.externalUrl()))
                .toList();
    }

    private static void validateTrack(PhotoRecommendationAiResponse.Track track) {
        if (track == null || isBlank(track.externalTrackId()) || isBlank(track.title())
                || isBlank(track.artistName()) || isBlank(track.albumImageUrl())
                || isBlank(track.externalUrl())) {
            throw new com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
