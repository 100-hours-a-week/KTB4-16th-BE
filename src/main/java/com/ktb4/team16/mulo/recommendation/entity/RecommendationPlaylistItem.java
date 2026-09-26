package com.ktb4.team16.mulo.recommendation.entity;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_playlist_items")
@Getter
@NoArgsConstructor
public class RecommendationPlaylistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recommendationPlaylistItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_playlist_id", nullable = false)
    private RecommendationPlaylist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_track_id", nullable = false)
    private MusicTrack musicTrack;

    private Integer musicOrder;

    private RecommendationPlaylistItem(RecommendationPlaylist playlist, MusicTrack musicTrack,
            Integer musicOrder) {
        this.playlist = playlist;
        this.musicTrack = musicTrack;
        this.musicOrder = musicOrder;
    }

    // 플레이리스트 안에서 표시할 곡과 순서를 생성한다.
    public static RecommendationPlaylistItem create(RecommendationPlaylist playlist,
            MusicTrack musicTrack, Integer musicOrder) {
        return new RecommendationPlaylistItem(playlist, musicTrack, musicOrder);
    }
}
