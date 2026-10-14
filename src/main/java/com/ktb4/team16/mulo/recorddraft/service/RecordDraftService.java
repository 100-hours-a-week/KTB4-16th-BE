package com.ktb4.team16.mulo.recorddraft.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest;
import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.recorddraft.repository.RecordDraftRepository;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordDraftService {

    private static final int DRAFT_VALIDITY_DAYS = 14;

    private final RecordDraftRepository recordDraftRepository;
    private final UploadService uploadService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<RecordDraft> findActiveRecordDraftForOwner(Long userId) {
        return recordDraftRepository.findByUser_UserId(userId)
                .filter(this::isActive);
    }

    @Transactional(readOnly = true)
    public String createPhotoUrl(Long userId, RecordDraft draft) {
        if (draft.getUpload() == null) {
            return null;
        }
        return uploadService.createDraftReadSignedUrl(userId, draft.getUpload().getUploadId());
    }

    @Transactional
    public void upsertRecordDraft(Long userId, RecordDraftUpsertRequest request) {
        User user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        RecordDraft draft = recordDraftRepository.findByUser_UserId(userId).orElse(null);
        Upload upload = resolveUpload(userId, request.uploadId(), draft, now);
        RecordDraftUpsertRequest.Location location = request.location();
        RecordDraftUpsertRequest.Music music = request.music();
        Byte moodScore = request.moodScore() == null ? (byte) 0 : request.moodScore().byteValue();

        if (draft == null) {
            draft = RecordDraft.create(
                    user,
                    location == null ? null : location.latitude(),
                    location == null ? null : location.longitude(),
                    location == null ? null : location.legalDongCode(),
                    location == null ? null : location.legalDongName(),
                    music == null ? null : music.externalTrackId(),
                    music == null ? null : music.title(),
                    music == null ? null : music.artistName(),
                    music == null ? null : music.albumImageUrl(),
                    music == null ? null : music.externalUrl(),
                    moodScore,
                    request.comment(),
                    upload,
                    now);
        } else {
            draft.replaceSnapshot(
                    location == null ? null : location.latitude(),
                    location == null ? null : location.longitude(),
                    location == null ? null : location.legalDongCode(),
                    location == null ? null : location.legalDongName(),
                    music == null ? null : music.externalTrackId(),
                    music == null ? null : music.title(),
                    music == null ? null : music.artistName(),
                    music == null ? null : music.albumImageUrl(),
                    music == null ? null : music.externalUrl(),
                    moodScore,
                    request.comment(),
                    upload,
                    now);
        }
        recordDraftRepository.saveAndFlush(draft);
    }

    @Transactional
    public void deleteRecordDraft(Long userId) {
        recordDraftRepository.deleteByUser_UserId(userId);
    }

    private Upload resolveUpload(
            Long userId,
            Long uploadId,
            RecordDraft currentDraft,
            LocalDateTime now
    ) {
        if (uploadId == null) {
            return null;
        }
        if (currentDraft != null && isActiveAt(currentDraft, now)
                && currentDraft.getUpload() != null
                && uploadId.equals(currentDraft.getUpload().getUploadId())) {
            return uploadService.findOwnedUploadForDraft(userId, uploadId);
        }
        return uploadService.findValidUpload(userId, uploadId);
    }

    private boolean isActive(RecordDraft draft) {
        return isActiveAt(draft, LocalDateTime.now(clock));
    }

    private boolean isActiveAt(RecordDraft draft, LocalDateTime now) {
        return now.isBefore(draft.getUpdatedAt().plusDays(DRAFT_VALIDITY_DAYS));
    }
}
