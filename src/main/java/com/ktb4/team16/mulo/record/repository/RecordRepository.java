package com.ktb4.team16.mulo.record.repository;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.NearbyTrackCandidateDto;
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

    // 법정동별 합계로 대표 동을 정하고, 그 동의 대표 장소 행을 한 쿼리에서 고른다.
    @Query(value = """
        WITH place_counts AS (
            SELECT p.place_id, p.legal_dong_code, p.legal_dong_name,
                COUNT(*) AS place_count, MAX(r.created_at) AS latest_record_at
            FROM records r
            JOIN places p ON p.place_id = r.place_id
            WHERE r.user_id = :userId
                AND r.deleted_at IS NULL
                AND r.created_at >= :startInclusive
                AND r.created_at < :endExclusive
            GROUP BY p.place_id, p.legal_dong_code, p.legal_dong_name
        ), ranked_places AS (
            SELECT pc.place_id, pc.legal_dong_code, pc.legal_dong_name,
                SUM(pc.place_count) OVER (PARTITION BY pc.legal_dong_code) AS dong_count,
                MAX(pc.latest_record_at) OVER (PARTITION BY pc.legal_dong_code) AS dong_latest,
                ROW_NUMBER() OVER (PARTITION BY pc.legal_dong_code
                    ORDER BY pc.place_count DESC, pc.latest_record_at DESC,
                        pc.place_id ASC) AS place_rank
            FROM place_counts pc
        )
        SELECT place_id, legal_dong_code, legal_dong_name
        FROM ranked_places
        WHERE place_rank = 1
        ORDER BY dong_count DESC, dong_latest DESC,
            legal_dong_code IS NULL ASC, legal_dong_code ASC
        """, nativeQuery = true)
    List<Object[]> findMonthlyTopPlaceRows(
            @Param("userId") Long userId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );

    // 집계 쿼리의 첫 결과를 기존 서비스가 사용하는 값 DTO로 변환한다.
    default List<MonthlyTopPlace> findMonthlyTopPlaces(Long userId, LocalDateTime startInclusive,
            LocalDateTime endExclusive, Pageable pageable) {
        return findMonthlyTopPlaceRows(userId, startInclusive, endExclusive, pageable).stream()
                .map(row -> new MonthlyTopPlace(((Number) row[0]).longValue(),
                        (String) row[1], (String) row[2]))
                .toList();
    }

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

    /** 활성 기록과 소유자 또는 현재 친구 관계를 한 SQL 조건으로 확인해 상세 접근을 허용한다. */
    @Query("""
        SELECT record
        FROM Record record
        WHERE record.recordId = :recordId
            AND record.deletedAt IS NULL
            AND record.user.deletedAt IS NULL
            AND (
                record.user.userId = :viewerId
                OR EXISTS (
                    SELECT friendship.friendshipId
                    FROM Friendship friendship
                    WHERE (friendship.userLow.userId = :viewerId
                            AND friendship.userHigh.userId = record.user.userId)
                        OR (friendship.userHigh.userId = :viewerId
                            AND friendship.userLow.userId = record.user.userId)
                )
            )
        """)
    Optional<Record> findActiveRecordVisibleToViewer(
            @Param("viewerId") Long viewerId,
            @Param("recordId") Long recordId
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

    // 현재 좌표의 bounding box 안에서 최근 활성 자물쇠 후보를 반경 판정 전 단계로 조회한다.
    @Query("""
        SELECT new com.ktb4.team16.mulo.record.dto.NearbyTrackCandidateDto(
            m.musicTrackId, m.title, m.artistName, p.latitude, p.longitude, r.createdAt
        )
        FROM Record r
        JOIN r.place p
        JOIN r.musicTrack m
        WHERE r.deletedAt IS NULL
            AND r.createdAt >= :createdAtFrom
            AND p.latitude BETWEEN :swLat AND :neLat
            AND p.longitude BETWEEN :swLng AND :neLng
        """)
    List<NearbyTrackCandidateDto> findNearbyTrackCandidates(
            @Param("swLat") BigDecimal swLat,
            @Param("neLat") BigDecimal neLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLng") BigDecimal neLng,
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
            r.recordId, r.place.placeId, m.musicTrackId, m.title, m.artistName, m.albumImageUrl, r.createdAt
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
