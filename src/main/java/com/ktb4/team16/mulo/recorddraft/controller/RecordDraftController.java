package com.ktb4.team16.mulo.recorddraft.controller;

import com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest;
import com.ktb4.team16.mulo.recorddraft.dto.response.RecordDraftResponse;
import com.ktb4.team16.mulo.recorddraft.dto.response.RecordDraftUpsertResponse;
import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.recorddraft.message.RecordDraftMessage;
import com.ktb4.team16.mulo.recorddraft.service.RecordDraftService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/record-draft")
@RequiredArgsConstructor
public class RecordDraftController {

    private static final int DRAFT_VALIDITY_DAYS = 14;

    private final RecordDraftService recordDraftService;

    @GetMapping
    public ResponseEntity<RecordDraftResponse> getRecordDraft(
            @AuthenticationPrincipal Long userId
    ) {
        Optional<RecordDraft> draft = recordDraftService.findActiveRecordDraftForOwner(userId);
        if (draft.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        RecordDraft recordDraft = draft.get();
        RecordDraftResponse.Data data = new RecordDraftResponse.Data(
                toLocation(recordDraft),
                toMusic(recordDraft),
                recordDraft.getMoodScore() == null ? (byte) 0 : recordDraft.getMoodScore(),
                recordDraft.getComment(),
                recordDraft.getUpload() == null ? null : recordDraft.getUpload().getUploadId(),
                recordDraftService.createPhotoUrl(userId, recordDraft),
                recordDraft.getUpdatedAt().plusDays(DRAFT_VALIDITY_DAYS));
        return ResponseEntity.ok(new RecordDraftResponse(
                RecordDraftMessage.RECORD_DRAFT_RETRIEVED.message(), data));
    }

    @PutMapping
    public RecordDraftUpsertResponse upsertRecordDraft(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecordDraftUpsertRequest request
    ) {
        recordDraftService.upsertRecordDraft(userId, request);
        return new RecordDraftUpsertResponse(RecordDraftMessage.RECORD_DRAFT_SAVED.message());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecordDraft(@AuthenticationPrincipal Long userId) {
        recordDraftService.deleteRecordDraft(userId);
    }

    private RecordDraftResponse.Location toLocation(RecordDraft draft) {
        if (draft.getLatitude() == null) {
            return null;
        }
        return new RecordDraftResponse.Location(
                draft.getLatitude(),
                draft.getLongitude(),
                draft.getLegalDongCode(),
                draft.getLegalDongName());
    }

    private RecordDraftResponse.Music toMusic(RecordDraft draft) {
        if (draft.getExternalTrackId() == null) {
            return null;
        }
        return new RecordDraftResponse.Music(
                draft.getExternalTrackId(),
                draft.getTitle(),
                draft.getArtistName(),
                draft.getAlbumImageUrl(),
                draft.getExternalUrl());
    }
}
