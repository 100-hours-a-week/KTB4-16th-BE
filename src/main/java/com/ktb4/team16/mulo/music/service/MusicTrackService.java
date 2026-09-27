package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicTrackService {

    private final MusicTrackRepository musicTrackRepository;

    @Transactional
    public MusicTrack findOrCreate(
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl
    ) {
        return musicTrackRepository.findByExternalTrackId(externalTrackId)
                .orElseGet(() -> musicTrackRepository.save(MusicTrack.create(
                        externalTrackId,
                        title,
                        artistName,
                        albumImageUrl,
                        externalUrl,
                        LocalDateTime.now()
                )));
    }
}
