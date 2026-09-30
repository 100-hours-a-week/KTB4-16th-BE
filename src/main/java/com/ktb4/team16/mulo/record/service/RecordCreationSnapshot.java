package com.ktb4.team16.mulo.record.service;

import java.time.LocalDateTime;

/** Scalar data captured while the Record creation transaction is open. */
public record RecordCreationSnapshot(
        Long recordId,
        Long userId,
        String photoObjectKey,
        Track track,
        String comment,
        LocalDateTime createdAt
) {
    public record Track(String title, String artistName, String externalTrackId) { }
}
