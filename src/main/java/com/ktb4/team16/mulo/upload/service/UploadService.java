package com.ktb4.team16.mulo.upload.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.upload.conversion.HeicImageConverter;
import com.ktb4.team16.mulo.upload.conversion.HeicImageConverter.ConvertedImage;
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
import java.util.Optional;
import java.util.TimeZone;
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
    private final HeicImageConverter heicImageConverter;

    @Transactional
    public UploadResponse upload(Long userId, MultipartFile photo) {
        ValidatedImage image = ImageFileValidator.validate(photo);
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        UploadImage uploadImage = prepareUploadImage(image);
        String objectKey = objectKey(userId, uploadImage.extension());
        boolean uploaded = false;
        try {
            gcsStorageService.upload(objectKey, uploadImage.content(), uploadImage.contentType());
            uploaded = true;
            Upload upload = uploadRepository.saveAndFlush(
                    Upload.create(user, objectKey, uploadImage.contentType(),
                            uploadImage.content().length));
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
        log.info("Upload lookup diagnostic: uploadId={}, userId={}, cutoff={}, clockZone={}, jvmTimezone={}",
                uploadId, userId, cutoff, clock.getZone(), TimeZone.getDefault().getID());
        Optional<Upload> upload = uploadRepository.findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                uploadId, userId, cutoff);
        log.info("Upload lookup result: uploadId={}, userId={}, result={}",
                uploadId, userId, upload.isPresent() ? "FOUND" : "NOT_FOUND");
        if (upload.isEmpty()) {
            logUploadMetadataDiagnostic(uploadId);
        }
        return upload.orElseThrow(UploadNotFoundException::new);
    }

    // Temporary diagnostics only; raw metadata must never determine upload validity.
    private void logUploadMetadataDiagnostic(Long uploadId) {
        try {
            Optional<Upload> storedUpload = uploadRepository.findById(uploadId);
            if (storedUpload.isPresent()) {
                Upload upload = storedUpload.get();
                log.info("Upload metadata diagnostic: uploadId={}, storedUserId={}, storedCreatedAt={}",
                        uploadId, upload.getUser().getUserId(), upload.getCreatedAt());
            } else {
                log.info("Upload metadata diagnostic: uploadId={}, result=NOT_FOUND", uploadId);
            }
        } catch (RuntimeException exception) {
            // Do not log exception details that could contain sensitive data.
            log.info("Upload metadata diagnostic: uploadId={}, result=UNAVAILABLE", uploadId);
        }
    }

    @Transactional
    public void deleteMetadata(Upload upload) {
        uploadRepository.delete(upload);
    }

    @Transactional(readOnly = true)
    public String createReadSignedUrl(Long userId, Long uploadId) {
        return gcsStorageService.createReadSignedUrl(findValidUpload(userId, uploadId).getImageUrl());
    }

    // TODO: 만료 cleanup에서 transaction commit 실패로 남은 GCS object도 회수한다.

    private String objectKey(Long userId, String extension) {
        return "uploads/" + userId + "/" + UUID.randomUUID() + "." + extension;
    }

    private UploadImage prepareUploadImage(ValidatedImage image) {
        if (!"heic".equals(image.extension())) {
            return new UploadImage(image.content(), image.contentType(), image.extension());
        }
        ConvertedImage converted = heicImageConverter.convert(image.content());
        return new UploadImage(converted.content(), converted.contentType(), converted.extension());
    }

    private record UploadImage(byte[] content, String contentType, String extension) {
    }
}
