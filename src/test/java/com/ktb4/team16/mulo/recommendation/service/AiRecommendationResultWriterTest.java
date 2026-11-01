package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResultTrack;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AiRecommendationResultWriterTest {
    @Mock private UserRepository userRepository;
    @Mock private AiRecommendationResultRepository resultRepository;
    @Mock private AiRecommendationResultTrackRepository resultTrackRepository;
    @Mock private User user;

    @Test
    void savesPhotoResultAndAllTracksInOrder() {
        AiRecommendationResultWriter writer = writer();
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(resultRepository.save(any(AiRecommendationResult.class))).thenAnswer(invocation -> {
            AiRecommendationResult result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 44L);
            return result;
        });

        Long resultId = writer.save(7L, AiRecommendationType.PHOTO, null,
                List.of("track-1", "track-2", "track-3"));

        assertThat(resultId).isEqualTo(44L);
        verify(resultTrackRepository).saveAll(org.mockito.ArgumentMatchers.argThat(this::hasExpectedTracks));
    }

    @Test
    void propagatesTrackWriteFailureFromTransactionalSave() throws NoSuchMethodException {
        AiRecommendationResultWriter writer = writer();
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(resultRepository.save(any(AiRecommendationResult.class))).thenAnswer(invocation -> {
            AiRecommendationResult result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 44L);
            return result;
        });
        doThrow(new IllegalStateException()).when(resultTrackRepository).saveAll(anyIterable());

        assertThatThrownBy(() -> writer.save(7L, AiRecommendationType.PHOTO, null, List.of("track-1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(AiRecommendationResultWriter.class
                .getMethod("save", Long.class, AiRecommendationType.class, Byte.class, List.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private AiRecommendationResultWriter writer() {
        return new AiRecommendationResultWriter(userRepository, resultRepository, resultTrackRepository);
    }

    private boolean hasExpectedTracks(Iterable<AiRecommendationResultTrack> tracks) {
        List<AiRecommendationResultTrack> savedTracks = StreamSupport.stream(tracks.spliterator(), false).toList();
        return savedTracks.size() == 3
                && savedTracks.get(0).getExternalTrackId().equals("track-1")
                && savedTracks.get(0).getRank().equals(1)
                && savedTracks.get(1).getExternalTrackId().equals("track-2")
                && savedTracks.get(1).getRank().equals(2)
                && savedTracks.get(2).getExternalTrackId().equals("track-3")
                && savedTracks.get(2).getRank().equals(3);
    }
}
