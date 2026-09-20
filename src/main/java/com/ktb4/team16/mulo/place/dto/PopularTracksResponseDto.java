package com.ktb4.team16.mulo.place.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class PopularTracksResponseDto {

    private final Long recordCount;
    private final List<Music> music;

    public PopularTracksResponseDto(
            Long recordCount,
            List<Music> music
    ) {
        this.recordCount = recordCount;
        this.music = music;
    }

    @Getter
    public static class Music {

        private final Integer rank;
        private final Long musicTrackId;
        private final String title;
        private final String artistName;
        private final Long count;

        public Music(Integer rank, Long musicTrackId, String title, String artistName, Long count) {
            this.rank = rank;
            this.musicTrackId = musicTrackId;
            this.title = title;
            this.artistName = artistName;
            this.count = count;
        }
    }
}
