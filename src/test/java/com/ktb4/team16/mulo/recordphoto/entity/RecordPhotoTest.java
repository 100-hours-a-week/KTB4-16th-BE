package com.ktb4.team16.mulo.recordphoto.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.record.entity.Record;
import org.junit.jupiter.api.Test;

class RecordPhotoTest {
    @Test
    void createsPhotoWithRecordMetadata() {
        Record record = new Record();

        RecordPhoto photo = RecordPhoto.create(record, "uploads/1/photo.jpg", "image/jpeg", 128L);

        assertThat(photo.getRecord()).isSameAs(record);
        assertThat(photo.getImageUrl()).isEqualTo("uploads/1/photo.jpg");
        assertThat(photo.getMimeType()).isEqualTo("image/jpeg");
        assertThat(photo.getFileSize()).isEqualTo(128L);
    }
}
