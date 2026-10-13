package com.ktb4.team16.mulo.recorddraft.repository;

import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordDraftRepository extends JpaRepository<RecordDraft, Long> {
    Optional<RecordDraft> findByUser_UserId(Long userId);

    void deleteByUser_UserId(Long userId);
}
