package com.ktb4.team16.mulo.record.repository;

import com.ktb4.team16.mulo.record.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.ktb4.team16.mulo.record.dto.RecordMarkerResponseDto;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {

    @Query("""
    SELECT new com.ktb4.team16.mulo.record.dto.RecordMarkerResponseDto(
        r.recordId,
        p.latitude,
        p.longitude
    )
    FROM Record r
    JOIN r.place p
    WHERE r.user.userId = :userId
        AND r.deletedAt is NULL
        AND p.latitude BETWEEN :swLat AND :neLat
        AND p.longitude BETWEEN :swLng AND :neLng
    """)
    List<RecordMarkerResponseDto> findMarkersInBounds(
        @Param("userId") Long userId,
        @Param("swLat") BigDecimal swLat,
        @Param("swLng") BigDecimal swLng,
        @Param("neLat") BigDecimal neLat,
        @Param("neLng") BigDecimal neLng
    );

}
