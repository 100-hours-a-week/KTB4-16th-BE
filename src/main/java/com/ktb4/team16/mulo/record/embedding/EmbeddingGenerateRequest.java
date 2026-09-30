package com.ktb4.team16.mulo.record.embedding;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record EmbeddingGenerateRequest(
        Long recordId,
        Long userId,
        String photoUrl,
        Track track,
        String comment,
        String createdAt
) {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    public static EmbeddingGenerateRequest from(
            RecordEmbeddingSnapshot snapshot,
            String signedPhotoUrl
    ) {
        String createdAt = snapshot.createdAt().atOffset(KST_OFFSET)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new EmbeddingGenerateRequest(
                snapshot.recordId(),
                snapshot.userId(),
                signedPhotoUrl,
                new Track(snapshot.track().title(), snapshot.track().artistName(),
                        snapshot.track().externalTrackId()),
                snapshot.comment(),
                createdAt);
    }

    public record Track(String title, String artistName, String externalTrackId) { }
}
