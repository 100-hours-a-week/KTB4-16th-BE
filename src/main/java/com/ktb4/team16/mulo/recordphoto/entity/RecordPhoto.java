package com.ktb4.team16.mulo.recordphoto.entity;

import com.ktb4.team16.mulo.record.entity.Record;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "record_photos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_record_photos_record", columnNames = "record_id")
})
@Getter
@NoArgsConstructor
public class RecordPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_photo_id")
    private Long recordPhotoId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    private Record record;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private RecordPhoto(Record record, String imageUrl, String mimeType, Long fileSize) {
        this.record = record;
        this.imageUrl = imageUrl;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }

    public static RecordPhoto create(
            Record record, String imageUrl, String mimeType, Long fileSize) {
        return new RecordPhoto(record, imageUrl, mimeType, fileSize);
    }
}
