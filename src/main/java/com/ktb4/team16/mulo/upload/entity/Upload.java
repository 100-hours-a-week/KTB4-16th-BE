package com.ktb4.team16.mulo.upload.entity;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Upload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "upload_id")
    private Long uploadId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private Upload(User user, String imageUrl, String mimeType, Long fileSize) {
        this.user = user;
        this.imageUrl = imageUrl;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    public static Upload create(User user, String imageUrl, String mimeType, long fileSize) {
        return new Upload(user, imageUrl, mimeType, fileSize);
    }
}
