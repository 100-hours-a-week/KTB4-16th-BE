package com.ktb4.team16.mulo.record.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmbeddingGenerateRequestTest {
    @Test
    void serializesKstOffsetAndNullCommentWithoutChangingWallClockTime() throws Exception {
        var snapshot = new RecordEmbeddingSnapshot(
                1024L,
                7L,
                "records/7/photo.jpg",
                new RecordEmbeddingSnapshot.Track("밤편지", "아이유", "track-123"),
                null,
                LocalDateTime.parse("2026-09-30T19:40:00"));

        EmbeddingGenerateRequest request = EmbeddingGenerateRequest.from(
                snapshot, "https://signed.example/photo");
        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(request.createdAt()).isEqualTo("2026-09-30T19:40:00+09:00");
        assertThat(json).contains("\"createdAt\":\"2026-09-30T19:40:00+09:00\"");
        assertThat(json).contains("\"comment\":null");
        assertThat(json).contains("\"photoUrl\":\"https://signed.example/photo\"");
        assertThat(json).contains("\"externalTrackId\":\"track-123\"");
    }
}
