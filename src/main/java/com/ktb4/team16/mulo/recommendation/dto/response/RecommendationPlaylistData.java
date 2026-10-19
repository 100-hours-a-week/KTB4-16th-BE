package com.ktb4.team16.mulo.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylistItem;
import java.util.List;

public record RecommendationPlaylistData(Playlist playlist,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean replaced) {
    // 조회 응답은 생성 여부 없이 반환하고 POST에서만 실제 교체 여부를 명시한다.
    public RecommendationPlaylistData(Playlist playlist) {
        this(playlist, null);
    }

    // 영속 엔티티를 공개 응답용 현재 플레이리스트 데이터로 변환한다.
    public static RecommendationPlaylistData from(RecommendationPlaylist playlist,
            List<RecommendationPlaylistItem> items) {
        return new RecommendationPlaylistData(new Playlist(playlist.getRecommendationPlaylistId(),
                items.stream().map(Track::from).toList()));
    }

    public record Playlist(Long recommendationPlaylistId, List<Track> tracks) {
    }

    public record Track(Long musicTrackId, String externalTrackId, String title, String artistName, String albumImageUrl,
            String externalUrl) {
        // 추천 항목의 곡 정보를 API 응답용 값으로 변환한다.
        public static Track from(RecommendationPlaylistItem item) {
            var track = item.getMusicTrack();
            return new Track(track.getMusicTrackId(), track.getExternalTrackId(), track.getTitle(), track.getArtistName(),
                    track.getAlbumImageUrl(), track.getExternalUrl());
        }
    }
}
