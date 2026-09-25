package com.ktb4.team16.mulo.upload.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.upload.dto.response.UploadResponse;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.exception.UploadNotFoundException;
import com.ktb4.team16.mulo.upload.message.UploadMessage;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.upload.validation.ImageFileValidator;
import com.ktb4.team16.mulo.upload.validation.ImageFileValidator.ValidatedImage;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {
    private static final long UPLOAD_VALIDITY_HOURS = 1L;

    private final UploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final GcsStorageService gcsStorageService;
    private final Clock clock;

    @Transactional
    public UploadResponse upload(Long userId, MultipartFile photo) {
        ValidatedImage image = ImageFileValidator.validate(photo);
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        String objectKey = objectKey(userId, image.extension());
        boolean uploaded = false;
        try {
            gcsStorageService.upload(objectKey, image.content(), image.contentType());
            uploaded = true;
            Upload upload = uploadRepository.saveAndFlush(
                    Upload.create(user, objectKey, image.contentType(), image.content().length));
            return new UploadResponse(
                    UploadMessage.UPLOAD_COMPLETED.message(),
                    new UploadResponse.Data(upload.getUploadId())
            );
        } catch (RuntimeException exception) {
            if (uploaded) {
                try {
                    gcsStorageService.delete(objectKey);
                } catch (RuntimeException compensationException) {
                    log.warn("GCS compensation delete failed for objectKey={}", objectKey,
                            compensationException);
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Upload findValidUpload(Long userId, Long uploadId) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(UPLOAD_VALIDITY_HOURS);
        return uploadRepository.findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                        uploadId, userId, cutoff)
                .orElseThrow(UploadNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public String createReadSignedUrl(Long userId, Long uploadId) {
        return gcsStorageService.createReadSignedUrl(findValidUpload(userId, uploadId).getImageUrl());
    }

    // TODO: 만료 cleanup에서 transaction commit 실패로 남은 GCS object도 회수한다.

    private String objectKey(Long userId, String extension) {
        return "uploads/" + userId + "/" + UUID.randomUUID() + "." + extension;
    }
}
