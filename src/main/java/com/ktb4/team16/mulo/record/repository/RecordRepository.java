package com.ktb4.team16.mulo.record.repository;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary;
import com.ktb4.team16.mulo.report.dto.MonthlyTopArtist;
import com.ktb4.team16.mulo.report.dto.MonthlyTopPlace;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordRepository extends JpaRepository<Record, Long> {

    // 기간 내 활성 기록이 있는 사용자만 월간 리포트 생성 대상으로 조회한다.
    @Query("""
        SELECT DISTINCT r.user.userId
        FROM Record r
        WHERE r.deletedAt IS NULL
            AND r.createdAt >= :startInclusive
            AND r.createdAt < :endExclusive
        ORDER BY r.user.userId ASC
        """)
    List<Long> findUsersWithActiveRecordsInPeriod(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    // 사용자·기간의 활성 기록 수와 평균 기분을 월간 스냅샷용으로 집계한다.
    @Query("""
        SELECT new com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary(
            COUNT(r), AVG(r.moodScore)
        )
        FROM Record r
        WHERE r.user.userId = :userId
            AND r.deletedAt IS NULL
            AND r.createdAt >= :startInclusive
            AND r.createdAt < :endExclusive
        """)
    Optional<MonthlyRecordSummary> findMonthlyRecordSummary(
            @Param("userId") Long userId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    // 기록 수 동률이면 가장 최근 기록이 있는 장소를 월간 대표 장소로 우선한다.
    @Query("""
        SELECT new com.ktb4.team16.mulo.report.dto.MonthlyTopPlace(r.place.placeId)
        FROM Record r
        WHERE r.user.userId = :userId
            AND r.deletedAt IS NULL
            AND r.createdAt >= :startInclusive
            AND r.createdAt < :endExclusive
        GROUP BY r.place.placeId
        ORDER BY COUNT(r) DESC, MAX(r.createdAt) DESC, r.place.placeId ASC
        """)
    List<MonthlyTopPlace> findMonthlyTopPlaces(
            @Param("userId") Long userId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );

    // 기록 수 동률이면 가장 최근 기록이 있는 아티스트를 월간 대표 아티스트로 우선한다.
    @Query("""
        SELECT new com.ktb4.team16.mulo.report.dto.MonthlyTopArtist(m.artistName)
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.deletedAt IS NULL
            AND r.createdAt >= :startInclusive
            AND r.createdAt < :endExclusive
        GROUP BY m.artistName
        ORDER BY COUNT(r) DESC, MAX(r.createdAt) DESC, m.artistName ASC
        """)
    List<MonthlyTopArtist> findMonthlyTopArtists(
            @Param("userId") Long userId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );

    Optional<Record> findByRecordIdAndUser_UserIdAndDeletedAtIsNull(
            Long recordId,
            Long userId
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse(
            p.legalDongCode, p.legalDongName, COUNT(r)
        )
        FROM Record r
        JOIN r.place p
        WHERE r.user.userId = :userId
            AND r.deletedAt IS NULL
        GROUP BY p.legalDongCode, p.legalDongName
        ORDER BY MAX(r.createdAt) DESC, COALESCE(p.legalDongCode, 'UNKNOWN') ASC
        """)
    List<RecordRegionGroupResponse> findMyRecordRegions(
            @Param("userId") Long userId
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

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.legalDongCode = :legalDongCode
            AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyRecordsByLegalDongCode(
            @Param("userId") Long userId,
            @Param("legalDongCode") String legalDongCode,
            Pageable pageable
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.legalDongCode IS NULL
            AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyRecordsInUnknownRegion(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.legalDongCode = :legalDongCode
            AND r.deletedAt IS NULL
            AND (r.createdAt < :cursorCreatedAt
                OR (r.createdAt = :cursorCreatedAt AND r.recordId < :cursorRecordId))
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyRecordsByLegalDongCodeAfterCursor(
            @Param("userId") Long userId,
            @Param("legalDongCode") String legalDongCode,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorRecordId") Long cursorRecordId,
            Pageable pageable
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto(
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, r.createdAt
        )
        FROM Record r
        JOIN r.musicTrack m
        WHERE r.user.userId = :userId
            AND r.place.legalDongCode IS NULL
            AND r.deletedAt IS NULL
            AND (r.createdAt < :cursorCreatedAt
                OR (r.createdAt = :cursorCreatedAt AND r.recordId < :cursorRecordId))
        ORDER BY r.createdAt DESC, r.recordId DESC
        """)
    List<MyPlaceRecordResponseDto> findMyRecordsInUnknownRegionAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorRecordId") Long cursorRecordId,
            Pageable pageable
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse(
            p.legalDongCode, p.legalDongName, COUNT(r)
        )
        FROM Record r
        JOIN r.place p
        WHERE r.user.userId = :userId
            AND p.legalDongCode = :legalDongCode
            AND r.deletedAt IS NULL
        GROUP BY p.legalDongCode, p.legalDongName
        """)
    Optional<RecordRegionGroupResponse> findMyRecordRegion(
            @Param("userId") Long userId,
            @Param("legalDongCode") String legalDongCode
    );

    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse(
            p.legalDongCode, p.legalDongName, COUNT(r)
        )
        FROM Record r
        JOIN r.place p
        WHERE r.user.userId = :userId
            AND p.legalDongCode IS NULL
            AND r.deletedAt IS NULL
        GROUP BY p.legalDongCode, p.legalDongName
        """)
    Optional<RecordRegionGroupResponse> findMyUnknownRecordRegion(
            @Param("userId") Long userId
    );
}
