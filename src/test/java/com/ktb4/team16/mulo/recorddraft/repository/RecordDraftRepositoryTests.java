package com.ktb4.team16.mulo.recorddraft.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.flyway.enabled=false", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecordDraftRepositoryTests {
    @Autowired
    private RecordDraftRepository recordDraftRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadRepository uploadRepository;

    @Test
    void savesAndFindsDraftWithNullableSnapshotAndUpload() {
        User user = createUser();
        LocalDateTime putAt = LocalDateTime.of(2026, 10, 8, 10, 30);
        RecordDraft draft = RecordDraft.create(user, null, null, null, null,
                null, null, null, null, null, null, null, null, putAt);

        RecordDraft saved = recordDraftRepository.saveAndFlush(draft);
        RecordDraft found = recordDraftRepository.findByUser_UserId(user.getUserId())
                .orElseThrow();

        assertThat(found.getRecordDraftId()).isEqualTo(saved.getRecordDraftId());
        assertThat(found.getLatitude()).isNull();
        assertThat(found.getUpload()).isNull();
        assertThat(found.getUpdatedAt()).isEqualTo(putAt);
    }

    @Test
    void updatesDraftAndDeletesItByUserId() {
        User user = createUser();
        LocalDateTime putAt = LocalDateTime.of(2026, 10, 8, 10, 30);
        recordDraftRepository.saveAndFlush(RecordDraft.create(user, null, null, null, null,
                null, null, null, null, null, null, null, null, putAt));

        RecordDraft found = recordDraftRepository.findByUser_UserId(user.getUserId())
                .orElseThrow();
        found.replaceSnapshot(null, null, null, null, "track", "title", "artist",
                "album", "external", (byte) 1, null, null, putAt.plusMinutes(1));
        recordDraftRepository.flush();

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId()))
                .get()
                .extracting(RecordDraft::getTitle)
                .isEqualTo("title");

        recordDraftRepository.deleteByUser_UserId(user.getUserId());

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId())).isEmpty();
    }

    @Test
    void enforcesOneDraftPerUserAndAllowsNullableUploadForeignKey() {
        User user = createUser();
        LocalDateTime putAt = LocalDateTime.of(2026, 10, 8, 10, 30);
        recordDraftRepository.saveAndFlush(RecordDraft.create(user, null, null, null, null,
                null, null, null, null, null, null, null, null, putAt));

        assertThatThrownBy(() -> recordDraftRepository.saveAndFlush(
                RecordDraft.create(user, null, null, null, null,
                        null, null, null, null, null, null, null, null, putAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsOptionalUploadAssociation() {
        User user = createUser();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Upload upload = uploadRepository.saveAndFlush(
                Upload.create(user, "uploads/" + suffix + ".jpg", "image/jpeg", 123L));
        LocalDateTime putAt = LocalDateTime.of(2026, 10, 8, 10, 30);
        RecordDraft draft = recordDraftRepository.saveAndFlush(RecordDraft.create(
                user, null, null, null, null, null, null, null, null, null,
                null, null, upload, putAt));

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId()))
                .get()
                .extracting(RecordDraft::getUpload)
                .isEqualTo(upload);
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userRepository.saveAndFlush(User.signup(
                suffix + "@example.test", "test-password-hash", "u" + suffix));
    }
}
