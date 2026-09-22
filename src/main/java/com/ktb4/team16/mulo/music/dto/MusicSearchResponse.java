package com.ktb4.team16.mulo.music.dto;

import com.ktb4.team16.mulo.music.domain.MusicProvider;
import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import java.util.List;

public record MusicSearchResponse(String message, List<Track> data) {
    private static final String SUCCESS_MESSAGE = "음악 검색에 성공했습니다.";

    // 서비스 검색 결과 목록을 공개 API 응답 형식으로 변환한다.
    public static MusicSearchResponse from(List<MusicSearchResult> results) {
        return new MusicSearchResponse(SUCCESS_MESSAGE,
                results.stream().map(Track::from).toList());
    }

    public record Track(
            MusicProvider provider,
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl
    ) {
        // 내부 검색 결과 한 건을 프론트가 사용하는 트랙 응답으로 변환한다.
        private static Track from(MusicSearchResult result) {
            return new Track(result.provider(), result.externalTrackId(), result.title(),
                    result.artistName(), result.albumImageUrl(), result.externalUrl());
        }
    }
}
