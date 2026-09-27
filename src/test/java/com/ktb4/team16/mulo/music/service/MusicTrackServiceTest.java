package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MusicTrackServiceTest {

    private final MusicTrackRepository repository = mock(MusicTrackRepository.class);
    private final MusicTrackService service = new MusicTrackService(repository);

    @Test
    void reusesExistingTrackWithoutSaving() {
        MusicTrack existing = MusicTrack.create(
                "track-id", "old title", "old artist", "old image", "old url",
                LocalDateTime.now());
        when(repository.findByExternalTrackId("track-id")).thenReturn(Optional.of(existing));

        MusicTrack result = service.findOrCreate(
                "track-id", "new title", "new artist", "new image", "new url");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(MusicTrack.class));
    }

    @Test
    void createsTrackFromRequestMetadataWhenMissing() {
        when(repository.findByExternalTrackId("track-id")).thenReturn(Optional.empty());
        MusicTrack saved = MusicTrack.create(
                "track-id", "title", "artist", "image", "url", LocalDateTime.now());
        when(repository.save(org.mockito.ArgumentMatchers.any(MusicTrack.class))).thenReturn(saved);

        MusicTrack result = service.findOrCreate(
                "track-id", "title", "artist", "image", "url");

        assertThat(result).isSameAs(saved);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(track ->
                track.getExternalTrackId().equals("track-id")
                        && track.getTitle().equals("title")
                        && track.getArtistName().equals("artist")
                        && track.getAlbumImageUrl().equals("image")
                        && track.getExternalUrl().equals("url")));
    }
}
