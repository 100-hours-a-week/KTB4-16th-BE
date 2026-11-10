package com.ktb4.team16.mulo.recommendation.dto.response;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import java.util.List;

public record RecommendationPlaylistData(Playlist playlist, boolean replaced) {
    public record Playlist(List<Track> tracks) { }

    public record Track(Long musicTrackId, String externalTrackId, String title, String artistName,
            String albumImageUrl, String externalUrl) {
        // 음악 카탈로그의 곡을 화면 세션 응답으로 변환하며 플레이리스트 저장 상태를 만들지 않는다.
        public static Track from(MusicTrack track) {
            return new Track(track.getMusicTrackId(), track.getExternalTrackId(), track.getTitle(), track.getArtistName(),
                    track.getAlbumImageUrl(), track.getExternalUrl());
        }
    }
}
