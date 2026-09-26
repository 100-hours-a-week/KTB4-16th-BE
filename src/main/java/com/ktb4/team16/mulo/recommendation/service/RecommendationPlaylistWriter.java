package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylistItem;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistItemRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationPlaylistWriter {
    private final UserRepository userRepository;
    private final RecommendationPlaylistRepository playlistRepository;
    private final RecommendationPlaylistItemRepository itemRepository;
    private final MusicTrackService musicTrackService;

    // 사용자 잠금 안에서 기존 플레이리스트를 새 추천 곡 목록으로 교체한다.
    @Transactional
    public RecommendationPlaylistData.Playlist replace(Long userId, List<RecommendedTrack> tracks) {
        var user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        playlistRepository.findByUserUserId(userId).ifPresent(existing -> {
            playlistRepository.delete(existing);
            playlistRepository.flush();
        });
        var playlist = playlistRepository.save(RecommendationPlaylist.create(user, LocalDateTime.now()));
        var items = java.util.stream.IntStream.range(0, tracks.size()).mapToObj(index -> {
            var track = tracks.get(index);
            var music = musicTrackService.findOrCreate(track.externalTrackId(), track.title(),
                    track.artistName(), track.albumImageUrl(), track.externalUrl());
            return itemRepository.save(RecommendationPlaylistItem.create(playlist, music, index + 1));
        }).toList();
        return RecommendationPlaylistData.from(playlist, items).playlist();
    }
}
