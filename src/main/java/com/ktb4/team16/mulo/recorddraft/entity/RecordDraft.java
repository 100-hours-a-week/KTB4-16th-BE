package com.ktb4.team16.mulo.recorddraft.entity;

import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "record_drafts")
@Getter
@NoArgsConstructor
public class RecordDraft {
    private static final int COORDINATE_SCALE = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_draft_id")
    private Long recordDraftId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(precision = 11, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "legal_dong_code", length = 20)
    private String legalDongCode;

    @Column(name = "legal_dong_name", length = 100)
    private String legalDongName;

    @Column(name = "external_track_id", length = 22)
    private String externalTrackId;

    @Column(length = 255)
    private String title;

    @Column(name = "artist_name", length = 255)
    private String artistName;

    @Column(name = "album_image_url", length = 255)
    private String albumImageUrl;

    @Column(name = "external_url", length = 255)
    private String externalUrl;

    @Column(name = "mood_score")
    private Byte moodScore;

    @Column(length = 80)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private Upload upload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private RecordDraft(
            User user,
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode,
            String legalDongName,
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            Byte moodScore,
            String comment,
            Upload upload,
            LocalDateTime putAt
    ) {
        this.user = user;
        setSnapshot(latitude, longitude, legalDongCode, legalDongName,
                externalTrackId, title, artistName, albumImageUrl, externalUrl,
                moodScore, comment, upload);
        this.createdAt = putAt;
        this.updatedAt = putAt;
    }

    public static RecordDraft create(
            User user,
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode,
            String legalDongName,
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            Byte moodScore,
            String comment,
            Upload upload,
            LocalDateTime putAt
    ) {
        return new RecordDraft(user, latitude, longitude, legalDongCode, legalDongName,
                externalTrackId, title, artistName, albumImageUrl, externalUrl,
                moodScore, comment, upload, putAt);
    }

    public void replaceSnapshot(
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode,
            String legalDongName,
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            Byte moodScore,
            String comment,
            Upload upload,
            LocalDateTime putAt
    ) {
        setSnapshot(latitude, longitude, legalDongCode, legalDongName,
                externalTrackId, title, artistName, albumImageUrl, externalUrl,
                moodScore, comment, upload);
        this.updatedAt = putAt;
    }

    private void setSnapshot(
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode,
            String legalDongName,
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            Byte moodScore,
            String comment,
            Upload upload
    ) {
        this.latitude = normalizeCoordinate(latitude);
        this.longitude = normalizeCoordinate(longitude);
        this.legalDongCode = legalDongCode;
        this.legalDongName = legalDongName;
        this.externalTrackId = externalTrackId;
        this.title = title;
        this.artistName = artistName;
        this.albumImageUrl = albumImageUrl;
        this.externalUrl = externalUrl;
        this.moodScore = moodScore;
        this.comment = comment;
        this.upload = upload;
    }

    private BigDecimal normalizeCoordinate(BigDecimal coordinate) {
        return coordinate == null
                ? null
                : coordinate.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
