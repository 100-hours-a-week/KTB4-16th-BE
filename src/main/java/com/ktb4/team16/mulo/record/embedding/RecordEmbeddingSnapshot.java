package com.ktb4.team16.mulo.record.embedding;

import java.time.LocalDateTime;

/** Scalar data captured while a Record transaction is open. */
public record RecordEmbeddingSnapshot(
        Long recordId,
        Long userId,
        String photoObjectKey,
        Track track,
        String comment,
        LocalDateTime createdAt
) {
    public record Track(String title, String artistName, String externalTrackId) { }
}
