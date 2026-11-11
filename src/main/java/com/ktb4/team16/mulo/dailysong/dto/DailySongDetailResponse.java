package com.ktb4.team16.mulo.dailysong.dto;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import java.time.LocalDate;

public record DailySongDetailResponse(String message, Data data) {
    public static DailySongDetailResponse from(DailySongJob job) {
        return new DailySongDetailResponse("오늘의 노래 조회 성공",
                new Data(job.getJobId(), job.getRecommendationDate(), Track.from(job.getMusicTrack())));
    }

    public record Data(Long jobId, LocalDate recommendationDate, Track track) {
    }

    public record Track(
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl
    ) {
        private static Track from(MusicTrack track) {
            return new Track(track.getExternalTrackId(), track.getTitle(), track.getArtistName(),
                    track.getAlbumImageUrl(), track.getExternalUrl());
        }
    }
}
