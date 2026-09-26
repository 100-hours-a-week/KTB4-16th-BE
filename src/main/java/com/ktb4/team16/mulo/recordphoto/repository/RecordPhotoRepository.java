package com.ktb4.team16.mulo.recordphoto.repository;

import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordPhotoRepository extends JpaRepository<RecordPhoto, Long> {
    Optional<RecordPhoto> findByRecord_RecordId(Long recordId);
}
