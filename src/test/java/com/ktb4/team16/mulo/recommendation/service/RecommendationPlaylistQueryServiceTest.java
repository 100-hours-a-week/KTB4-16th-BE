package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylistItem;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistItemRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import com.ktb4.team16.mulo.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaylistQueryServiceTest {

    @Mock
    private RecommendationPlaylistRepository playlistRepository;

    @Mock
    private RecommendationPlaylistItemRepository itemRepository;

    @InjectMocks
    private RecommendationPlaylistQueryService queryService;

    @Test
    void returnsNullPlaylistWhenNoPlaylistExists() {
        when(playlistRepository.findByUserUserId(7L)).thenReturn(Optional.empty());

        RecommendationPlaylistData result = queryService.getCurrentPlaylist(7L);

        assertThat(result.playlist()).isNull();
        verify(itemRepository, never()).findAllByPlaylistRecommendationPlaylistIdOrderByMusicOrderAsc(
                anyLong());
    }

    @Test
    void returnsTracksInMusicOrder() {
        User user = User.signup("user@example.com", "password-hash", "mulo");
        RecommendationPlaylist playlist = RecommendationPlaylist.create(user, LocalDateTime.now());
        MusicTrack firstTrack = MusicTrack.create("1234567890123456789012", "first", "artist1",
                "https://image.example/1", "https://open.spotify.com/track/1234567890123456789012",
                LocalDateTime.now());
        MusicTrack secondTrack = MusicTrack.create("abcdefghijklmnopqrstuv", "second", "artist2",
                "https://image.example/2", "https://open.spotify.com/track/abcdefghijklmnopqrstuv",
                LocalDateTime.now());
        RecommendationPlaylistItem first = RecommendationPlaylistItem.create(playlist, firstTrack, 1);
        RecommendationPlaylistItem second = RecommendationPlaylistItem.create(playlist, secondTrack, 2);
        when(playlistRepository.findByUserUserId(7L)).thenReturn(Optional.of(playlist));
        when(itemRepository.findAllByPlaylistRecommendationPlaylistIdOrderByMusicOrderAsc(
                playlist.getRecommendationPlaylistId())).thenReturn(List.of(first, second));

        RecommendationPlaylistData result = queryService.getCurrentPlaylist(7L);

        assertThat(result.playlist().tracks()).extracting(RecommendationPlaylistData.Track::title)
                .containsExactly("first", "second");
    }
}
