package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResultTrack;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationPlaylistWriter {
    private final UserRepository userRepository;
    private final MusicTrackService musicTrackService;
    private final AiRecommendationResultRepository resultRepository;
    private final AiRecommendationResultTrackRepository resultTrackRepository;

    // 곡 카탈로그와 평가 이력을 원자적으로 기록하고 화면에 표시할 목록만 반환한다.
    @Transactional
    public ReplaceResult replace(Long userId, byte recommendationAttempt, List<RecommendedTrack> tracks) {
        var user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        var responseTracks = tracks.stream().map(track -> RecommendationPlaylistData.Track.from(
                musicTrackService.findOrCreate(track.externalTrackId(), track.title(),
                        track.artistName(), track.albumImageUrl(), track.externalUrl()))).toList();
        var result = resultRepository.save(AiRecommendationResult.create(
                user, AiRecommendationType.PLAYLIST, recommendationAttempt, LocalDateTime.now()));
        var resultTracks = IntStream.range(0, tracks.size())
                .mapToObj(index -> AiRecommendationResultTrack.create(
                        result, tracks.get(index).externalTrackId(), index + 1))
                .toList();
        resultTrackRepository.saveAll(resultTracks);
        return new ReplaceResult(new RecommendationPlaylistData.Playlist(responseTracks), result.getId());
    }

    public record ReplaceResult(RecommendationPlaylistData.Playlist playlist, Long recommendationId) { }
}
