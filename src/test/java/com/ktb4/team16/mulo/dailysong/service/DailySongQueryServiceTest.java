package com.ktb4.team16.mulo.dailysong.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySongQueryServiceTest {
    @Mock
    private DailySongJobRepository jobs;

    @InjectMocks
    private DailySongQueryService service;

    @Test
    void returnsCompletedDailySongForAuthenticatedOwnerWithoutRecommendationReason() {
        DailySongJob job = org.mockito.Mockito.mock(DailySongJob.class);
        MusicTrack track = org.mockito.Mockito.mock(MusicTrack.class);
        LocalDate recommendationDate = LocalDate.of(2026, 11, 11);
        when(job.getJobId()).thenReturn(803L);
        when(job.getRecommendationDate()).thenReturn(recommendationDate);
        when(job.getMusicTrack()).thenReturn(track);
        when(track.getExternalTrackId()).thenReturn("1234567890123456789012");
        when(track.getTitle()).thenReturn("테스트 곡");
        when(track.getArtistName()).thenReturn("테스트 가수");
        when(track.getAlbumImageUrl()).thenReturn("https://example.test/album");
        when(track.getExternalUrl()).thenReturn("https://example.test/track");
        when(jobs.findByJobIdAndUserIdAndStatus(803L, 35L, DailySongJobStatus.COMPLETED))
                .thenReturn(Optional.of(job));

        var response = service.getDailySong(35L, 803L);

        assertThat(response.message()).isEqualTo("오늘의 노래 조회 성공");
        assertThat(response.data().jobId()).isEqualTo(803L);
        assertThat(response.data().recommendationDate()).isEqualTo(recommendationDate);
        assertThat(response.data().track().title()).isEqualTo("테스트 곡");
        assertThat(response.data().track().artistName()).isEqualTo("테스트 가수");
        assertThat(response.data().track().albumImageUrl()).isEqualTo("https://example.test/album");
        assertThat(response.data().track().externalUrl()).isEqualTo("https://example.test/track");
        verify(jobs).findByJobIdAndUserIdAndStatus(803L, 35L, DailySongJobStatus.COMPLETED);
    }

    @Test
    void treatsUnavailableJobAsNotFound() {
        when(jobs.findByJobIdAndUserIdAndStatus(803L, 35L, DailySongJobStatus.COMPLETED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDailySong(35L, 803L))
                .isInstanceOf(NotificationDomainException.class)
                .satisfies(exception -> assertThat(((NotificationDomainException) exception).errorCode())
                        .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_FOUND));
        verify(jobs).findByJobIdAndUserIdAndStatus(803L, 35L, DailySongJobStatus.COMPLETED);
    }
}
