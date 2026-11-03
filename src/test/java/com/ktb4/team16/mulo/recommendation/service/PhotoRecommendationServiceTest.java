package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiGateway;
import com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiClient.PhotoRecommendationAiResponse;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.upload.service.UploadService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoRecommendationServiceTest {
    @Mock private UploadService uploadService;
    @Mock private PhotoRecommendationAiGateway aiClient;
    @Mock private AiRecommendationResultWriter resultWriter;

    @Test
    void savesPhotoRecommendationAfterReceivingFiveTracks() {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse("  사진 🌄\n그대로  ", tracks(5), false));
        when(resultWriter.save(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(AiRecommendationType.PHOTO),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyList())).thenReturn(44L);

        var result = service.recommend(7L, 123L);

        assertThat(result.recommendationId()).isEqualTo(44L);
        assertThat(result.recommendReason()).isEqualTo("  사진 🌄\n그대로  ");
        assertThat(result.tracks()).hasSize(5);
        assertThat(result.tracks().getFirst().externalTrackId()).isEqualTo("id-1");
        verify(uploadService).createReadSignedUrl(7L, 123L);
        verify(resultWriter).save(7L, AiRecommendationType.PHOTO, null,
                List.of("id-1", "id-2", "id-3", "id-4", "id-5"));
    }

    @Test
    void returnsAllFiveRecommendations() {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse(null, tracks(5), false));

        var result = service.recommend(7L, 123L);
        assertThat(result.tracks()).hasSize(5);
        assertThat(result.recommendReason()).isNull();
    }

    @Test
    void rejectsFourTracksInsteadOfTruncatingOrReturningPartialRecommendations() {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse(null, tracks(4), false));

        assertThatThrownBy(() -> service.recommend(7L, 123L))
                .isInstanceOf(com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException.class);
    }

    @Test
    void rejectsSixTracksInsteadOfTruncatingToFive() {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse(null, tracks(6), false));

        assertThatThrownBy(() -> service.recommend(7L, 123L))
                .isInstanceOf(com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidAlbumImageUrls")
    void rejectsMissingOrBlankAlbumImageUrl(String albumImageUrl) {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse(null, List.of(
                        track("id", "title", "artist", albumImageUrl, "url")), false));

        assertThatThrownBy(() -> service.recommend(7L, 123L))
                .isInstanceOf(com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException.class);
    }

    private static Stream<Arguments> invalidAlbumImageUrls() {
        return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("   "));
    }

    @Test
    void convertsDegradedResponseToAiError() {
        PhotoRecommendationService service = service();
        when(uploadService.createReadSignedUrl(7L, 123L)).thenReturn("https://signed.example/photo");
        when(aiClient.recommend("https://signed.example/photo")).thenReturn(
                new PhotoRecommendationAiResponse(null, List.of(), true));

        assertThatThrownBy(() -> service.recommend(7L, 123L))
                .isInstanceOf(com.ktb4.team16.mulo.recommendation.client.PhotoRecommendationAiException.class);
        verify(uploadService, never()).deleteMetadata(org.mockito.ArgumentMatchers.any());
        verify(resultWriter, never()).save(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private PhotoRecommendationService service() {
        return new PhotoRecommendationService(uploadService, aiClient, resultWriter);
    }

    private static PhotoRecommendationAiResponse.Track track(String id, String title,
            String artist, String album, String url) {
        return new PhotoRecommendationAiResponse.Track(title, artist, id, "spotify:track:" + id,
                album, url);
    }

    private static List<PhotoRecommendationAiResponse.Track> tracks(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> track("id-" + index, "title-" + index, "artist", "album", "url"))
                .toList();
    }
}
