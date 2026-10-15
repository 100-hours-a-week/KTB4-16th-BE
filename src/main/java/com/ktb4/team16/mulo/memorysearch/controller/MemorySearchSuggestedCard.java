package com.ktb4.team16.mulo.memorysearch.controller;

import java.util.List;

/** 일부 조건 제안 카드와 AI가 제공한 일치 조건을 공개한다. */
public record MemorySearchSuggestedCard(Long recordId, Long musicTrackId, String title,
        String artistName, Long placeId, String placeName, String date,
        String weatherCondition, String albumImageUrl, List<String> matchedConditions) {

    /** DB 카드에 해당 ID의 AI 일치 조건만 결합한다. */
    public static MemorySearchSuggestedCard from(MemorySearchCard card,
            List<String> matchedConditions) {
        return new MemorySearchSuggestedCard(card.recordId(), card.musicTrackId(),
                card.title(), card.artistName(), card.placeId(), card.placeName(),
                card.date(), card.weatherCondition(), card.albumImageUrl(),
                List.copyOf(matchedConditions));
    }
}
