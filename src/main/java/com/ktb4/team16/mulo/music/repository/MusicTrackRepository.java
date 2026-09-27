package com.ktb4.team16.mulo.music.repository;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicTrackRepository extends JpaRepository<MusicTrack, Long> {

    Optional<MusicTrack> findByExternalTrackId(String externalTrackId);
}
