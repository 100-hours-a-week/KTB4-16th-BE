package com.ktb4.team16.mulo.recordphoto.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@DataJpaTest(properties = "spring.flyway.enabled=false", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecordPhotoRepositoryTests {
    @Autowired
    private RecordPhotoRepository recordPhotoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsPhotoByRecordId() {
        long recordId = insertRecord();
        insertRecordPhoto(recordId, "uploads/1/photo.jpg");

        assertThat(recordPhotoRepository.findByRecord_RecordId(recordId))
                .isPresent()
                .get()
                .extracting(RecordPhoto::getImageUrl)
                .isEqualTo("uploads/1/photo.jpg");
    }

    @Test
    void returnsEmptyWhenRecordHasNoPhoto() {
        long recordId = insertRecord();

        assertThat(recordPhotoRepository.findByRecord_RecordId(recordId)).isEmpty();
    }

    private long insertRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long userId = insert(
                "INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                suffix + "@example.test", "test-hash", suffix);
        long placeId = insert("""
                INSERT INTO places (legal_dong_code, legal_dong_name, latitude, longitude)
                VALUES (?, ?, ?, ?)
                """, "1111010100", "테스트동",
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
        long musicTrackId = insert("""
                INSERT INTO music_tracks (external_track_id, title, artist_name, album_image_url, external_url)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString().substring(0, 20), "title", "artist", "album", "external");
        return insert("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, placeId, musicTrackId, 0, LocalDateTime.now());
    }

    private void insertRecordPhoto(long recordId, String imageUrl) {
        jdbcTemplate.update("""
                INSERT INTO record_photos (record_id, image_url, mime_type, file_size)
                VALUES (?, ?, ?, ?)
                """, recordId, imageUrl, "image/jpeg", 128L);
    }

    private long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
