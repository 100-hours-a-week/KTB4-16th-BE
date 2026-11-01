package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
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
public class AiRecommendationResultWriter {
    private final UserRepository userRepository;
    private final AiRecommendationResultRepository resultRepository;
    private final AiRecommendationResultTrackRepository resultTrackRepository;

    @Transactional
    public Long save(Long userId, AiRecommendationType type, Byte attempt, List<String> externalTrackIds) {
        var user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        var result = resultRepository.save(AiRecommendationResult.create(user, type, attempt, LocalDateTime.now()));
        var tracks = IntStream.range(0, externalTrackIds.size())
                .mapToObj(index -> AiRecommendationResultTrack.create(
                        result, externalTrackIds.get(index), index + 1))
                .toList();
        resultTrackRepository.saveAll(tracks);
        return result.getId();
    }
}
