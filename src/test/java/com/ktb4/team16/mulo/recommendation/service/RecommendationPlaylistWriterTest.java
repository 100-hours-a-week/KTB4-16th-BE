package com.ktb4.team16.mulo.recommendation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistItemRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaylistWriterTest {
    @Mock private UserRepository userRepository;
    @Mock private RecommendationPlaylistRepository playlistRepository;
    @Mock private RecommendationPlaylistItemRepository itemRepository;
    @Mock private MusicTrackService musicTrackService;
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

        writer.replace(7L, tracks());

        InOrder order = inOrder(userRepository, playlistRepository, itemRepository);
        order.verify(userRepository).findByUserIdAndDeletedAtIsNullForUpdate(7L);
        order.verify(playlistRepository).findByUserUserId(7L);
        order.verify(playlistRepository).delete(existing);
        order.verify(playlistRepository).flush();
        order.verify(playlistRepository).save(any());
        order.verify(itemRepository, org.mockito.Mockito.times(3)).save(any());
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
