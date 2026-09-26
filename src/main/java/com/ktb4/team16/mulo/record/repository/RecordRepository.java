package com.ktb4.team16.mulo.record.repository;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.entity.Record;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordRepository extends JpaRepository<Record, Long> {

    Optional<Record> findByRecordIdAndUser_UserIdAndDeletedAtIsNull(
            Long recordId,
            Long userId
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse(
            p.placeId, COUNT(r), p.legalDongCode, p.legalDongName, p.latitude, p.longitude
        )
        FROM Record r
        JOIN r.place p
        WHERE r.deletedAt IS NULL
            AND r.createdAt >= :createdAtFrom
            AND p.latitude BETWEEN :swLat AND :neLat
            AND p.longitude BETWEEN :swLng AND :neLng
        GROUP BY p.placeId, p.legalDongCode, p.legalDongName, p.latitude, p.longitude
        """)
    List<AllRecordMarkerResponse> findAllRecordMarkersInBounds(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng,
            @Param("createdAtFrom") LocalDateTime createdAtFrom
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto(
            m.musicTrackId, m.title, m.artistName, COUNT(r), MAX(r.createdAt)
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.place.placeId IN :placeIds
            AND r.deletedAt IS NULL
            AND r.createdAt >= :createdAtFrom
        GROUP BY m.musicTrackId, m.title, m.artistName
        ORDER BY COUNT(r) DESC, MAX(r.createdAt) DESC, m.musicTrackId ASC
        """)
    List<PopularTrackAggregateDto> findPopularTrackAggregates(
            @Param("placeIds") List<Long> placeIds,
            @Param("createdAtFrom") LocalDateTime createdAtFrom
    );

    @Query("""
        SELECT COUNT(r)
        FROM Record r
        WHERE r.place.placeId IN :placeIds
            AND r.deletedAt IS NULL
            AND r.createdAt >= :createdAtFrom
        """)
    long countActiveRecordsAtPlaces(
            @Param("placeIds") List<Long> placeIds,
            @Param("createdAtFrom") LocalDateTime createdAtFrom
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse(
            p.placeId, p.legalDongName, COUNT(r), p.latitude, p.longitude
        )
        FROM Record r
        JOIN r.place p
        WHERE r.user.userId = :userId
            AND r.deletedAt IS NULL
            AND p.latitude BETWEEN :swLat AND :neLat
            AND p.longitude BETWEEN :swLng AND :neLng
        GROUP BY p.placeId, p.legalDongName, p.latitude, p.longitude
        """)
    List<MyPlaceMarkerResponse> findMyPlaceMarkersInBounds(
            @Param("userId") Long userId,
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.placeId IN :placeIds
            AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyPlaceRecordsFirstPage(
            @Param("userId") Long userId,
            @Param("placeIds") List<Long> placeIds,
            Pageable pageable
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.placeId IN :placeIds
            AND r.deletedAt IS NULL
            AND (r.createdAt < :cursorCreatedAt
                OR (r.createdAt = :cursorCreatedAt AND r.recordId < :cursorRecordId))
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyPlaceRecordsAfterCursor(
            @Param("userId") Long userId,
            @Param("placeIds") List<Long> placeIds,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorRecordId") Long cursorRecordId,
            Pageable pageable
    );
}
