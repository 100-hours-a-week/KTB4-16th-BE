package com.ktb4.team16.mulo.recommendation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResultTrack;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistItemRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaylistWriterTest {
    @Mock private UserRepository userRepository;
    @Mock private RecommendationPlaylistRepository playlistRepository;
    @Mock private RecommendationPlaylistItemRepository itemRepository;
    @Mock private MusicTrackService musicTrackService;
    @Mock private AiRecommendationResultRepository resultRepository;
    @Mock private AiRecommendationResultTrackRepository resultTrackRepository;
    @InjectMocks private RecommendationPlaylistWriter writer;

    @Test
    void replacesExistingPlaylistAfterLockingUser() {
        User user = User.signup("user@example.com", "password-hash", "mulo");
        RecommendationPlaylist existing = RecommendationPlaylist.create(user, LocalDateTime.now());
        MusicTrack track = MusicTrack.create("1234567890123456789012", "title", "artist",
                "https://image.example", "https://open.spotify.com/track/1234567890123456789012",
                LocalDateTime.now());
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.of(user));
        when(playlistRepository.findByUserUserId(7L)).thenReturn(Optional.of(existing));
        when(musicTrackService.findOrCreate(any(), any(), any(), any(), any())).thenReturn(track);
        when(playlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resultRepository.save(any(AiRecommendationResult.class))).thenAnswer(invocation -> {
            AiRecommendationResult result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 55L);
            return result;
        });

        var result = writer.replace(7L, (byte) 2, tracks());

        InOrder order = inOrder(userRepository, playlistRepository, itemRepository);
        order.verify(userRepository).findByUserIdAndDeletedAtIsNullForUpdate(7L);
        order.verify(playlistRepository).findByUserUserId(7L);
        order.verify(playlistRepository).delete(existing);
        order.verify(playlistRepository).flush();
        order.verify(playlistRepository).save(any());
        order.verify(itemRepository, org.mockito.Mockito.times(3)).save(any());
        verify(resultRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getRecommendationAttempt().equals((byte) 2)));
        verify(resultTrackRepository).saveAll(org.mockito.ArgumentMatchers.argThat(this::hasExpectedTracks));
        assertThat(result.playlist()).isNotNull();
        assertThat(result.recommendationId()).isEqualTo(55L);
    }

    @Test
    void propagatesHistoryTrackWriteFailureFromTransactionalReplace() throws NoSuchMethodException {
        User user = User.signup("user@example.com", "password-hash", "mulo");
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.of(user));
        when(musicTrackService.findOrCreate(any(), any(), any(), any(), any()))
                .thenReturn(MusicTrack.create("1234567890123456789012", "title", "artist",
                        "https://image.example", "https://open.spotify.com/track/1234567890123456789012",
                        LocalDateTime.now()));
        when(playlistRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resultRepository.save(any(AiRecommendationResult.class))).thenAnswer(invocation -> {
            AiRecommendationResult result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 55L);
            return result;
        });
        doThrow(new IllegalStateException()).when(resultTrackRepository).saveAll(anyIterable());

        assertThatThrownBy(() -> writer.replace(7L, (byte) 1, tracks()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(RecommendationPlaylistWriter.class
                .getMethod("replace", Long.class, byte.class, List.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private boolean hasExpectedTracks(Iterable<AiRecommendationResultTrack> tracks) {
        List<AiRecommendationResultTrack> savedTracks = StreamSupport.stream(tracks.spliterator(), false).toList();
        return savedTracks.size() == 3
                && savedTracks.get(0).getExternalTrackId().equals("1234567890123456789012")
                && savedTracks.get(0).getRank().equals(1)
                && savedTracks.get(1).getExternalTrackId().equals("abcdefghijklmnopqrstuv")
                && savedTracks.get(1).getRank().equals(2)
                && savedTracks.get(2).getExternalTrackId().equals("zyxwvutsrqponmlkjihgfe")
                && savedTracks.get(2).getRank().equals(3);
    }

    private List<RecommendedTrack> tracks() {
        return List.of(track("1234567890123456789012", "first"),
                track("abcdefghijklmnopqrstuv", "second"),
                track("zyxwvutsrqponmlkjihgfe", "third"));
    }

    private RecommendedTrack track(String id, String title) {
        return new RecommendedTrack(id, title, "artist", "https://image.example/cover",
                "https://open.spotify.com/track/" + id);
    }
}
