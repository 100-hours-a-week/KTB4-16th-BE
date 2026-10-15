package com.ktb4.team16.mulo.recorddraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.service.PreparedWeather;
import com.ktb4.team16.mulo.record.service.RecordService;
import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.recorddraft.repository.RecordDraftRepository;
import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentMatchers;

@SpringBootTest
@ActiveProfiles("weather-it")
class RecordDraftRecordCreationMysqlIntegrationTest {
    private static final String EXPECTED_DATABASE = "mulo_weather_it";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BigDecimal LATITUDE = new BigDecimal("37.5012345");
    private static final BigDecimal LONGITUDE = new BigDecimal("127.0012345");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordService recordService;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private RecordDraftRepository recordDraftRepository;
    @Autowired private RecordPhotoRepository recordPhotoRepository;
    @MockitoBean private Clock clock;
    @MockitoBean private UploadService uploadService;
    @MockitoBean private GcsStorageService gcsStorageService;

    private User user;
    private Upload upload;
    private String trackId;

    @BeforeEach
    void createIsolatedFixture() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(EXPECTED_DATABASE);
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:mysql://127\\.0\\.0\\.1:(3307|3309)/" + EXPECTED_DATABASE + ".*");
        }
        assertThat(AopUtils.isAopProxy(recordService)).isTrue();
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(KST);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user = userRepository.saveAndFlush(User.signup(
                suffix + "@example.test", "test-password-hash", "u" + suffix.substring(0, 8)));
        Place place = placeRepository.saveAndFlush(new Place(
                "1111010100", "종로1가", LATITUDE, LONGITUDE));
        upload = uploadRepository.saveAndFlush(
                Upload.create(user, "uploads/" + suffix + ".jpg", "image/jpeg", 123L));
        trackId = "draft-" + suffix;
        recordDraftRepository.saveAndFlush(RecordDraft.create(
                user, LATITUDE, LONGITUDE, "1111010100", "종로1가", trackId,
                "title", "artist", "album", "external", (byte) 0, "draft",
                upload, LocalDateTime.now(clock)));
        when(uploadService.findOwnedUploadForDraft(user.getUserId(), upload.getUploadId()))
                .thenReturn(upload);
        doAnswer(invocation -> {
            uploadRepository.delete(invocation.getArgument(0));
            return null;
        }).when(uploadService).deleteMetadata(any(Upload.class));
    }

    @AfterEach
    void removeOnlyFixtureRows() {
        if (user != null) {
            Long userId = user.getUserId();
            jdbcTemplate.update("DELETE FROM record_photos WHERE record_id IN "
                    + "(SELECT record_id FROM records WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM records WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM record_drafts WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM uploads WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM places WHERE latitude = ? AND longitude = ?",
                    LATITUDE, LONGITUDE);
            if (trackId != null) {
                jdbcTemplate.update("DELETE FROM music_tracks WHERE external_track_id = ?", trackId);
            }
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    @Test
    void deletesDraftBeforeUploadMetadataAndKeepsRecordPhotoObjectReference() {
        RecordEmbeddingSnapshot snapshot = recordService.createRecord(user.getUserId(), request(),
                new KakaoRegionClient.LegalRegion("1111010100", "종로1가"),
                PreparedWeather.withoutWeather());

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId())).isEmpty();
        assertThat(uploadRepository.findById(upload.getUploadId())).isEmpty();
        RecordPhoto photo = recordPhotoRepository.findByRecord_RecordId(snapshot.recordId())
                .orElseThrow();
        assertThat(photo.getImageUrl()).isEqualTo(upload.getImageUrl());
        assertThat(photo.getImageUrl()).startsWith("uploads/");
    }

    @Test
    void rollsBackDraftDeletionWhenFinalRecordTransactionFails() {
        doThrow(new IllegalStateException("metadata deletion failed"))
                .when(uploadService).deleteMetadata(any(Upload.class));

        assertThatThrownBy(() -> recordService.createRecord(user.getUserId(), request(),
                new KakaoRegionClient.LegalRegion("1111010100", "종로1가"),
                PreparedWeather.withoutWeather()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId())).isPresent();
        assertThat(uploadRepository.findById(upload.getUploadId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM records WHERE user_id = ?", Long.class, user.getUserId()))
                .isZero();
    }

    @Test
    void keepsDraftUploadWhenFinalRecordUsesDifferentUpload() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Upload recordUpload = uploadRepository.saveAndFlush(
                Upload.create(user, "uploads/record-" + suffix + ".jpg", "image/jpeg", 456L));
        when(uploadService.findValidUpload(user.getUserId(), recordUpload.getUploadId()))
                .thenReturn(recordUpload);

        RecordEmbeddingSnapshot snapshot = recordService.createRecord(user.getUserId(),
                request(recordUpload.getUploadId()),
                new KakaoRegionClient.LegalRegion("1111010100", "종로1가"),
                PreparedWeather.withoutWeather());

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId())).isEmpty();
        assertThat(uploadRepository.existsById(upload.getUploadId())).isTrue();
        assertThat(uploadRepository.findById(recordUpload.getUploadId())).isEmpty();
        RecordPhoto photo = recordPhotoRepository.findByRecord_RecordId(snapshot.recordId())
                .orElseThrow();
        assertThat(photo.getImageUrl()).isEqualTo(recordUpload.getImageUrl());
        verify(uploadService).deleteMetadata(recordUpload);
        verify(uploadService, never()).deleteMetadata(upload);
        verify(gcsStorageService, never()).delete(ArgumentMatchers.anyString());
    }

    private RecordCreateRequest request() {
        return request(upload.getUploadId());
    }

    private RecordCreateRequest request(Long requestUploadId) {
        return new RecordCreateRequest(
                new RecordCreateRequest.Location(LATITUDE, LONGITUDE, "1111010100", "종로1가"),
                new RecordCreateRequest.Music(trackId, "title", "artist", "album", "external"),
                0,
                "draft",
                requestUploadId);
    }
}
