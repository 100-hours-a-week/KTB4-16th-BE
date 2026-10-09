package com.ktb4.team16.mulo.recorddraft.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RecordDraftTest {
    @Test
    void createsDraftWithSnapshotAndNormalizedCoordinates() {
        User user = mock(User.class);
        Upload upload = mock(Upload.class);
        LocalDateTime putAt = LocalDateTime.of(2026, 10, 8, 10, 30);

        RecordDraft draft = RecordDraft.create(user,
                new BigDecimal("37.123456789"), new BigDecimal("127.123456789"),
                "4159012700", "오산동", "track-id", "title", "artist",
                "album-image", "external-url", (byte) 0, "comment", upload, putAt);

        assertThat(draft.getRecordDraftId()).isNull();
        assertThat(draft.getUser()).isSameAs(user);
        assertThat(draft.getLatitude()).isEqualByComparingTo("37.1234568");
        assertThat(draft.getLongitude()).isEqualByComparingTo("127.1234568");
        assertThat(draft.getLegalDongCode()).isEqualTo("4159012700");
        assertThat(draft.getLegalDongName()).isEqualTo("오산동");
        assertThat(draft.getExternalTrackId()).isEqualTo("track-id");
        assertThat(draft.getTitle()).isEqualTo("title");
        assertThat(draft.getArtistName()).isEqualTo("artist");
        assertThat(draft.getAlbumImageUrl()).isEqualTo("album-image");
        assertThat(draft.getExternalUrl()).isEqualTo("external-url");
        assertThat(draft.getMoodScore()).isEqualTo((byte) 0);
        assertThat(draft.getComment()).isEqualTo("comment");
        assertThat(draft.getUpload()).isSameAs(upload);
        assertThat(draft.getCreatedAt()).isEqualTo(putAt);
        assertThat(draft.getUpdatedAt()).isEqualTo(putAt);
    }

    @Test
    void replacesWholeSnapshotIncludingNullsAndKeepsCreationTime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime nextPutAt = createdAt.plusDays(1);
        RecordDraft draft = RecordDraft.create(mock(User.class),
                new BigDecimal("37.5"), new BigDecimal("127.0"),
                "code", "name", "track", "title", "artist", "album", "url",
                (byte) 5, "old", mock(Upload.class), createdAt);

        draft.replaceSnapshot(null, null, null, null, null, null, null, null, null,
                null, null, null, nextPutAt);

        assertThat(draft.getLatitude()).isNull();
        assertThat(draft.getLongitude()).isNull();
        assertThat(draft.getLegalDongCode()).isNull();
        assertThat(draft.getLegalDongName()).isNull();
        assertThat(draft.getExternalTrackId()).isNull();
        assertThat(draft.getTitle()).isNull();
        assertThat(draft.getArtistName()).isNull();
        assertThat(draft.getAlbumImageUrl()).isNull();
        assertThat(draft.getExternalUrl()).isNull();
        assertThat(draft.getMoodScore()).isNull();
        assertThat(draft.getComment()).isNull();
        assertThat(draft.getUpload()).isNull();
        assertThat(draft.getCreatedAt()).isEqualTo(createdAt);
        assertThat(draft.getUpdatedAt()).isEqualTo(nextPutAt);
    }

    @Test
    void roundsHalfUpAtTheSeventhDecimalPlace() {
        RecordDraft draft = RecordDraft.create(mock(User.class),
                new BigDecimal("37.12345675"), new BigDecimal("-127.12345675"),
                null, null, null, null, null, null, null, null, null, null,
                LocalDateTime.of(2026, 10, 8, 10, 30));

        assertThat(draft.getLatitude()).isEqualByComparingTo("37.1234568");
        assertThat(draft.getLongitude()).isEqualByComparingTo("-127.1234568");
    }
}
