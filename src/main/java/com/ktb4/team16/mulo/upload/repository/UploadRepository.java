package com.ktb4.team16.mulo.upload.repository;

import com.ktb4.team16.mulo.upload.entity.Upload;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, Long> {
    Optional<Upload> findByUploadIdAndUser_UserIdAndCreatedAtAfter(
            Long uploadId, Long userId, LocalDateTime createdAt);
}
