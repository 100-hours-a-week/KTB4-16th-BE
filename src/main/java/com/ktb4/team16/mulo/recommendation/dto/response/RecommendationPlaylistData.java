package com.ktb4.team16.mulo.recommendation.dto.response;

import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylistItem;
import java.util.List;

public record RecommendationPlaylistData(Playlist playlist) {
    // 영속 엔티티를 공개 응답용 현재 플레이리스트 데이터로 변환한다.
    public static RecommendationPlaylistData from(RecommendationPlaylist playlist,
            List<RecommendationPlaylistItem> items) {
        return new RecommendationPlaylistData(new Playlist(playlist.getRecommendationPlaylistId(),
                items.stream().map(Track::from).toList()));
    }

    public record Playlist(Long recommendationPlaylistId, List<Track> tracks) {
    }

    public record Track(Long musicTrackId, String title, String artistName, String externalUrl) {
        // 추천 항목의 곡 정보를 API 응답용 값으로 변환한다.
        public static Track from(RecommendationPlaylistItem item) {
            var track = item.getMusicTrack();
            return new Track(track.getMusicTrackId(), track.getTitle(), track.getArtistName(),
                    track.getExternalUrl());
        }
    }
}
