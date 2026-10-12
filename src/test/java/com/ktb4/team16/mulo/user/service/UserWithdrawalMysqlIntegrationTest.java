package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** 실제 MySQL에서 탈퇴가 활성 기록만 삭제하고 사진 메타데이터를 보존하는지 검증한다. */
@SpringBootTest
@Transactional
class UserWithdrawalMysqlIntegrationTest {
    @Autowired UserWithdrawalService withdrawalService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    /** 활성 기록과 토큰만 갱신하고 기존 삭제 시각·사진·계정 식별자 재사용을 검증한다. */
    @Test
    void withdrawalPreservesPreviousDeletionAndPhotoMetadata() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.saveAndFlush(User.signup(
                unique + "@example.test", passwordEncoder.encode("Correct123!"),
                unique.substring(0, 10)));
        Long userId = user.getUserId();
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                user, UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
                LocalDateTime.now().plusDays(7)));
        jdbcTemplate.update("INSERT INTO places (latitude, longitude) VALUES (37.5, 127.0)");
        Long placeId = jdbcTemplate.queryForObject("SELECT MAX(place_id) FROM places", Long.class);
        jdbcTemplate.update("""
                INSERT INTO music_tracks
                    (external_track_id, title, artist_name, album_image_url, external_url)
                VALUES (?, 'title', 'artist', 'album', 'external')
                """, unique.substring(0, 20));
        Long trackId = jdbcTemplate.queryForObject("SELECT MAX(music_track_id) FROM music_tracks", Long.class);
        LocalDateTime oldDeletion = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        jdbcTemplate.update("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score)
                VALUES (?, ?, ?, 1)
                """, userId, placeId, trackId);
        Long activeRecordId = jdbcTemplate.queryForObject("SELECT MAX(record_id) FROM records", Long.class);
        jdbcTemplate.update("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score, deleted_at)
                VALUES (?, ?, ?, 1, ?)
                """, userId, placeId, trackId, oldDeletion);
        Long previouslyDeletedRecordId = jdbcTemplate.queryForObject(
                "SELECT MAX(record_id) FROM records", Long.class);
        jdbcTemplate.update("""
                INSERT INTO record_photos (record_id, image_url, mime_type, file_size)
                VALUES (?, 'object/key', 'image/jpeg', 100)
                """, activeRecordId);

        withdrawalService.withdraw(userId, "Correct123!");
        entityManager.flush();

        LocalDateTime withdrawnAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM users WHERE user_id = ?", LocalDateTime.class, userId);
        assertThat(withdrawnAt).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM records WHERE record_id = ?", LocalDateTime.class,
                activeRecordId)).isEqualTo(withdrawnAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM records WHERE record_id = ?", LocalDateTime.class,
                previouslyDeletedRecordId)).isEqualTo(oldDeletion);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_photos WHERE record_id = ?", Integer.class,
                activeRecordId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM refresh_tokens WHERE user_id = ?", LocalDateTime.class,
                userId)).isEqualTo(withdrawnAt);
        User replacement = userRepository.saveAndFlush(User.signup(
                user.getEmail(), passwordEncoder.encode("Different123!"), user.getNickname()));
        assertThat(replacement.getUserId()).isNotEqualTo(userId);
    }
}
