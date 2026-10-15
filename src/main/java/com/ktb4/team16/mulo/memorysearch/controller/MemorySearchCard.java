package com.ktb4.team16.mulo.memorysearch.controller;

import com.ktb4.team16.mulo.record.entity.Record;
import java.time.format.DateTimeFormatter;

/** 검증된 자물쇠를 채팅과 전체보기에서 재사용한다. 날씨가 없는 기록의 weatherCondition은 null이다. */
public record MemorySearchCard(Long recordId, Long musicTrackId, String title,
        String artistName, Long placeId, String placeName, String date,
        String weatherCondition, String albumImageUrl) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** AI 텍스트가 아닌 활성 본인 자물쇠의 DB 필드로 카드 값을 만든다. */
    public static MemorySearchCard from(Record record) {
        return new MemorySearchCard(record.getRecordId(),
                record.getMusicTrack().getMusicTrackId(),
                record.getMusicTrack().getTitle(),
                record.getMusicTrack().getArtistName(),
                record.getPlace().getPlaceId(),
                record.getPlace().getLegalDongName(),
                record.getCreatedAt().format(DATE_FORMAT),
                record.getWeatherCondition() == null ? null
                        : record.getWeatherCondition().name(),
                record.getMusicTrack().getAlbumImageUrl());
    }
}
