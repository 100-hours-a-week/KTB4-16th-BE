package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RecordCreationOrchestratorTest {
    private static final RecordCreateRequest REQUEST = new RecordCreateRequest(
            new RecordCreateRequest.Location(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null, null),
            new RecordCreateRequest.Music("track-123", "밤편지", "아이유", "album", "external"),
            10, "비 냄새 좋았던 저녁", 11L);
    private static final RecordEmbeddingSnapshot SNAPSHOT = new RecordEmbeddingSnapshot(
            1024L, 7L, "records/7/photo.jpg",
            new RecordEmbeddingSnapshot.Track("밤편지", "아이유", "track-123"),
            "비 냄새 좋았던 저녁", LocalDateTime.parse("2026-09-30T19:40:00"));

    @Mock private RecordService recordService;
    @Mock private RecordCreationPreparationService preparationService;
    @Mock private RecordEmbeddingSubmitter embeddingSubmitter;

    @Test
    void submitsEmbeddingAfterRecordServiceReturnsAndKeepsCreateResponse() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather weather = new PreparedWeather(
                new BigDecimal("20.0"), Record.WeatherCondition.CLEAR);
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(weather);
        when(recordService.createRecord(7L, REQUEST, region, weather)).thenReturn(SNAPSHOT);

        RecordCreateResponse response = orchestrator().createRecord(7L, REQUEST);

        assertThat(response).isEqualTo(new RecordCreateResponse(1024L));
        InOrder order = inOrder(preparationService, recordService, embeddingSubmitter);
        order.verify(preparationService).preparePlace(REQUEST);
        order.verify(preparationService).prepareWeather(REQUEST);
        order.verify(recordService).createRecord(7L, REQUEST, region, weather);
        order.verify(embeddingSubmitter).submit(SNAPSHOT);
    }

    @Test
    void doesNotSubmitEmbeddingWhenRecordTransactionFails() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather weather = PreparedWeather.withoutWeather();
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(weather);
        when(recordService.createRecord(eq(7L), eq(REQUEST), eq(region), eq(weather)))
                .thenThrow(new IllegalStateException("commit failed"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddingSubmitter);
    }

    @Test
    void retriesOnlyNamedPlaceOrMusicTrackUniqueConflictsOnce() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather weather = PreparedWeather.withoutWeather();
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(weather);
        when(recordService.createRecord(7L, REQUEST, region, weather))
                .thenThrow(uniqueConflict("places.uk_places_coordinates"))
                .thenReturn(SNAPSHOT);

        assertThat(orchestrator().createRecord(7L, REQUEST))
                .isEqualTo(new RecordCreateResponse(1024L));

        org.mockito.Mockito.verify(recordService, times(2))
                .createRecord(7L, REQUEST, region, weather);
        org.mockito.Mockito.verify(preparationService).preparePlace(REQUEST);
        org.mockito.Mockito.verify(preparationService).prepareWeather(REQUEST);
        org.mockito.Mockito.verify(embeddingSubmitter).submit(SNAPSHOT);
    }

    @Test
    void doesNotRetryOtherUniqueConstraintsOrGeneralDatabaseErrors() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather weather = PreparedWeather.withoutWeather();
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(weather);
        RuntimeException unrelatedUnique = uniqueConflict("uk_records_other_constraint");
        when(recordService.createRecord(7L, REQUEST, region, weather))
                .thenThrow(unrelatedUnique);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST))).isSameAs(unrelatedUnique);
        org.mockito.Mockito.verify(recordService)
                .createRecord(7L, REQUEST, region, weather);
        verifyNoInteractions(embeddingSubmitter);

        org.mockito.Mockito.reset(recordService);
        RuntimeException generalDatabaseError = new DataIntegrityViolationException(
                "database unavailable", new SQLException("connection lost", "08006"));
        when(recordService.createRecord(7L, REQUEST, region, weather))
                .thenThrow(generalDatabaseError);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST)))
                .isSameAs(generalDatabaseError);
        org.mockito.Mockito.verify(recordService)
                .createRecord(7L, REQUEST, region, weather);
        verifyNoInteractions(embeddingSubmitter);
    }

    @Test
    void doesNotRetryMoreThanOnceAndNeverEmbedsAfterFinalFailure() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather weather = PreparedWeather.withoutWeather();
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(weather);
        RuntimeException secondConflict = uniqueConflict("music_tracks.uk_music_tracks_external_track_id");
        when(recordService.createRecord(7L, REQUEST, region, weather))
                .thenThrow(uniqueConflict("music_tracks.uk_music_tracks_external_track_id"))
                .thenThrow(secondConflict);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST))).isSameAs(secondConflict);

        org.mockito.Mockito.verify(recordService, times(2))
                .createRecord(7L, REQUEST, region, weather);
        org.mockito.Mockito.verify(preparationService).preparePlace(REQUEST);
        org.mockito.Mockito.verify(preparationService).prepareWeather(REQUEST);
        verifyNoInteractions(embeddingSubmitter);
    }

    @Test
    void doesNotStartRecordTransactionWhenPreparationFails() {
        IllegalStateException failure = new IllegalStateException("Kakao failed");
        when(preparationService.preparePlace(REQUEST)).thenThrow(failure);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST)))
                .isSameAs(failure);
        verifyNoInteractions(recordService, embeddingSubmitter);
    }

    @Test
    void continuesRecordCreationWhenWeatherPreparationHasNoWeather() {
        KakaoRegionClient.LegalRegion region = new KakaoRegionClient.LegalRegion("code", "name");
        PreparedWeather noWeather = PreparedWeather.withoutWeather();
        when(preparationService.preparePlace(REQUEST)).thenReturn(region);
        when(preparationService.prepareWeather(REQUEST)).thenReturn(noWeather);
        when(recordService.createRecord(7L, REQUEST, region, noWeather)).thenReturn(SNAPSHOT);

        RecordCreateResponse response = orchestrator().createRecord(7L, REQUEST);

        assertThat(response).isEqualTo(new RecordCreateResponse(1024L));
        org.mockito.Mockito.verify(recordService).createRecord(7L, REQUEST, region, noWeather);
    }

    private RecordCreationOrchestrator orchestrator() {
        return new RecordCreationOrchestrator(preparationService, recordService, embeddingSubmitter);
    }

    private RuntimeException uniqueConflict(String constraintName) {
        ConstraintViolationException hibernateException = new ConstraintViolationException(
                "duplicate key", new SQLException("duplicate key", "23000", 1062),
                constraintName);
        return new DataIntegrityViolationException("unique constraint violation", hibernateException);
    }
}
