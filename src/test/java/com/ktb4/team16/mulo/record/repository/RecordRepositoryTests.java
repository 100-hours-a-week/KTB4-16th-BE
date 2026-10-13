package com.ktb4.team16.mulo.record.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.response.AllRecordMarkerResponse;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.report.dto.MonthlyRecordSummary;
import com.ktb4.team16.mulo.report.dto.MonthlyTopArtist;
import com.ktb4.team16.mulo.report.dto.MonthlyTopPlace;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@DataJpaTest(properties = "spring.flyway.enabled=false", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecordRepositoryTests {

    private static final BigDecimal SW_LAT = new BigDecimal("-34.0000000");
    private static final BigDecimal SW_LNG = new BigDecimal("-151.0000000");
    private static final BigDecimal NE_LAT = new BigDecimal("-32.0000000");
    private static final BigDecimal NE_LNG = new BigDecimal("-149.0000000");
    private static final LocalDateTime FIRST_DAY = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final String LEGAL_DONG_CODE = "1111010100";
    private static final String LEGAL_DONG_NAME = "테스트동";

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private MonthlyReportRepository monthlyReportRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allRecordMarkersCountOnlyRecentActiveInBoundsRecordsPerPlace() {
        long userId = insertUser();
        long trackId = insertTrack("track");
        long firstInBounds = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long secondInBounds = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.1000000", "-150.1000000");
        long outOfBounds = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "10.0000000", "20.0000000");
        LocalDateTime createdAtFrom = FIRST_DAY.plusDays(7);
        insertRecord(userId, firstInBounds, trackId, createdAtFrom, null);
        insertRecord(userId, firstInBounds, trackId, createdAtFrom.plusDays(1), null);
        insertRecord(userId, firstInBounds, trackId, createdAtFrom.minusSeconds(1), null);
        insertRecord(userId, firstInBounds, trackId, createdAtFrom.plusDays(2),
                createdAtFrom.plusDays(3));
        insertRecord(userId, secondInBounds, trackId, createdAtFrom.plusDays(1), null);
        insertRecord(userId, outOfBounds, trackId, createdAtFrom.plusDays(1), null);

        List<AllRecordMarkerResponse> markers = recordRepository.findAllRecordMarkersInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG, createdAtFrom);

        assertThat(markers).hasSize(2);
        assertThat(markers).filteredOn(marker -> marker.placeId().equals(firstInBounds))
                .singleElement()
                .satisfies(marker -> {
                    assertThat(marker.recordsCount()).isEqualTo(2L);
                    assertThat(marker.legalDongCode()).isEqualTo(LEGAL_DONG_CODE);
                    assertThat(marker.legalDongName()).isEqualTo(LEGAL_DONG_NAME);
                    assertThat(marker.latitude()).isEqualByComparingTo("-33.0000000");
                    assertThat(marker.longitude()).isEqualByComparingTo("-150.0000000");
                });
        assertThat(markers).filteredOn(marker -> marker.placeId().equals(secondInBounds))
                .singleElement()
                .satisfies(marker -> assertThat(marker.recordsCount()).isEqualTo(1L));
        assertThat(placeRepository.findById(outOfBounds)).isPresent();
    }

    @Test
    void findsOnlyActiveRecordOwnedByRequestedUser() {
        long owner = insertUser();
        long other = insertUser();
        long placeId = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long trackId = insertTrack("detail");
        long active = insertRecord(owner, placeId, trackId, FIRST_DAY, null);
        long deleted = insertRecord(owner, placeId, trackId, FIRST_DAY.plusDays(1), FIRST_DAY.plusDays(2));
        long otherRecord = insertRecord(other, placeId, trackId, FIRST_DAY.plusDays(2), null);

        assertThat(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(active, owner))
                .isPresent()
                .get()
                .extracting(Record::getRecordId)
                .isEqualTo(active);
        assertThat(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(deleted, owner))
                .isEmpty();
        assertThat(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(otherRecord, owner))
                .isEmpty();
    }

    /** 친구 관계의 성립·삭제가 상세 조회 권한에 즉시 반영되는지 실제 DB에서 확인한다. */
    @Test
    void activeRecordDetailIsVisibleOnlyToOwnerOrCurrentFriend() {
        long owner = insertUser();
        long viewer = insertUser();
        long stranger = insertUser();
        long placeId = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long trackId = insertTrack("friend-detail");
        long active = insertRecord(owner, placeId, trackId, FIRST_DAY, null);
        long deleted = insertRecord(owner, placeId, trackId,
                FIRST_DAY.plusDays(1), FIRST_DAY.plusDays(2));

        assertThat(recordRepository.findActiveRecordVisibleToViewer(owner, active)).isPresent();
        assertThat(recordRepository.findActiveRecordVisibleToViewer(viewer, active)).isEmpty();
        jdbcTemplate.update("INSERT INTO friend_requests (requester_id, addressee_id) VALUES (?, ?)",
                viewer, owner);
        assertThat(recordRepository.findActiveRecordVisibleToViewer(viewer, active)).isEmpty();

        jdbcTemplate.update("DELETE FROM friend_requests WHERE requester_id = ? AND addressee_id = ?",
                viewer, owner);
        jdbcTemplate.update("INSERT INTO friendships (user_low_id, user_high_id) VALUES (?, ?)",
                Math.min(owner, viewer), Math.max(owner, viewer));
        assertThat(recordRepository.findActiveRecordVisibleToViewer(viewer, active)).isPresent();
        assertThat(recordRepository.findActiveRecordVisibleToViewer(viewer, deleted)).isEmpty();
        assertThat(recordRepository.findActiveRecordVisibleToViewer(stranger, active)).isEmpty();

        jdbcTemplate.update("DELETE FROM friendships WHERE user_low_id = ? AND user_high_id = ?",
                Math.min(owner, viewer), Math.max(owner, viewer));
        assertThat(recordRepository.findActiveRecordVisibleToViewer(viewer, active)).isEmpty();
        assertThat(recordRepository.findActiveRecordVisibleToViewer(owner, active)).isPresent();
    }

    @Test
    void popularTracksUseCountThenLatestRecordThenTrackIdOrder() {
        long userId = insertUser();
        long firstPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long secondPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.1000000", "-150.1000000");
        long excludedPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.2000000", "-150.2000000");
        long mostCount = insertTrack("most");
        long tiedFirst = insertTrack("tied-first");
        long tiedSecond = insertTrack("tied-second");
        long older = insertTrack("older");
        LocalDateTime createdAtFrom = FIRST_DAY.plusDays(3);
        insertRecord(userId, firstPlace, mostCount, FIRST_DAY.plusDays(3), null);
        insertRecord(userId, firstPlace, mostCount, FIRST_DAY.plusDays(4), null);
        insertRecord(userId, secondPlace, mostCount, FIRST_DAY.plusDays(5), null);
        insertRecord(userId, firstPlace, tiedFirst, FIRST_DAY.plusDays(4), null);
        insertRecord(userId, secondPlace, tiedFirst, FIRST_DAY.plusDays(9), null);
        insertRecord(userId, firstPlace, tiedSecond, FIRST_DAY.plusDays(4), null);
        insertRecord(userId, secondPlace, tiedSecond, FIRST_DAY.plusDays(9), null);
        insertRecord(userId, firstPlace, older, FIRST_DAY.plusDays(4), null);
        insertRecord(userId, secondPlace, older, FIRST_DAY.plusDays(8), null);
        insertRecord(userId, firstPlace, mostCount, FIRST_DAY.plusDays(2), null);
        insertRecord(userId, firstPlace, tiedFirst, FIRST_DAY.plusDays(10), FIRST_DAY.plusDays(11));
        insertRecord(userId, excludedPlace, mostCount, FIRST_DAY.plusDays(12), null);

        List<Long> placeIds = List.of(firstPlace, secondPlace);
        List<PopularTrackAggregateDto> tracks = recordRepository.findPopularTrackAggregates(
                placeIds, createdAtFrom);

        assertThat(tracks).extracting(PopularTrackAggregateDto::musicTrackId)
                .containsExactly(mostCount, tiedFirst, tiedSecond, older);
        assertThat(tracks).extracting(PopularTrackAggregateDto::count)
                .containsExactly(3L, 2L, 2L, 2L);
        assertThat(tracks.get(1).latestRecordCreatedAt()).isEqualTo(FIRST_DAY.plusDays(9));
        assertThat(recordRepository.countActiveRecordsAtPlaces(placeIds, createdAtFrom)).isEqualTo(9L);
    }

    @Test
    void monthlyAggregationUsesOnlyActiveRecordsInPeriodAndResolvesTiesByLatestRecord() {
        long userId = insertUser();
        long anotherUserId = insertUser();
        long firstPlace = insertPlace(LEGAL_DONG_CODE, "첫장소", "-33.0000000", "-150.0000000");
        long secondPlace = insertPlace(LEGAL_DONG_CODE, "둘장소", "-33.1000000", "-150.1000000");
        long firstTrack = insertTrack("first", "첫아티스트");
        long secondTrack = insertTrack("second", "둘아티스트");
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 0, 0);

        insertRecord(userId, firstPlace, firstTrack, (byte) 10, start.plusDays(2), null);
        insertRecord(userId, firstPlace, firstTrack, (byte) 30, start.plusDays(10), null);
        insertRecord(userId, secondPlace, secondTrack, (byte) 20, start.plusDays(3), null);
        insertRecord(userId, secondPlace, secondTrack, (byte) 40, start.plusDays(20), null);
        insertRecord(userId, secondPlace, secondTrack, (byte) 50, start.plusDays(21), start.plusDays(22));
        insertRecord(userId, firstPlace, firstTrack, (byte) -10, start.minusSeconds(1), null);
        insertRecord(userId, firstPlace, firstTrack, (byte) -10, end, null);
        insertRecord(anotherUserId, firstPlace, firstTrack, (byte) -10, start.plusDays(5), null);

        assertThat(recordRepository.findUsersWithActiveRecordsInPeriod(start, end))
                .containsExactly(userId, anotherUserId);
        MonthlyRecordSummary summary = recordRepository.findMonthlyRecordSummary(userId, start, end)
                .orElseThrow();
        MonthlyTopPlace topPlace = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();
        MonthlyTopArtist topArtist = recordRepository.findMonthlyTopArtists(
                userId, start, end, PageRequest.of(0, 1)).getFirst();

        assertThat(summary.recordCount()).isEqualTo(4L);
        assertThat(summary.averageMoodScore()).isEqualTo(25.0);
        assertThat(topPlace.placeId()).isEqualTo(secondPlace);
        assertThat(topPlace.legalDongCode()).isEqualTo(LEGAL_DONG_CODE);
        assertThat(topPlace.legalDongName()).isEqualTo("둘장소");
        assertThat(topArtist.artistName()).isEqualTo("둘아티스트");
    }

    // 법정동별 건수와 최신 시각이 같으면 분류된 동을 미분류 그룹보다 우선한다.
    @Test
    void monthlyTopPlaceRanksKnownDongBeforeUnknownWhenCountAndTimeTie() {
        long userId = insertUser();
        long first = insertPlace(null, null, "-33.0000000", "-150.0000000");
        long second = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.1000000", "-150.1000000");
        long track = insertTrack("monthly-place");
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 0, 0);
        insertRecord(userId, first, track, start, null);
        insertRecord(userId, first, track, start.plusDays(2), null);
        insertRecord(userId, second, track, start, null);
        insertRecord(userId, second, track, start.plusDays(2), null);

        MonthlyTopPlace top = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();
        assertThat(top.placeId()).isEqualTo(second);
        assertThat(top.legalDongCode()).isEqualTo(LEGAL_DONG_CODE);
        assertThat(top.legalDongName()).isEqualTo(LEGAL_DONG_NAME);
    }

    // 같은 법정동에 속한 서로 다른 장소의 기록을 합산해 대표 동을 선정한다.
    @Test
    void monthlyTopPlaceCountsRecordsAcrossPlacesInSameLegalDong() {
        long userId = insertUser();
        long first = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.0000000", "-150.0000000");
        long second = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.1000000", "-150.1000000");
        long other = insertPlace("1111010200", "다른동",
                "-33.2000000", "-150.2000000");
        long track = insertTrack("monthly-dong-total");
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        insertRecord(userId, first, track, start.plusDays(1), null);
        insertRecord(userId, first, track, start.plusDays(2), null);
        insertRecord(userId, second, track, start.plusDays(3), null);
        insertRecord(userId, second, track, start.plusDays(4), null);
        insertRecord(userId, other, track, start.plusDays(20), null);
        insertRecord(userId, other, track, start.plusDays(21), null);
        insertRecord(userId, other, track, start.plusDays(22), null);

        MonthlyTopPlace top = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();

        assertThat(top.legalDongCode()).isEqualTo(LEGAL_DONG_CODE);
        assertThat(top.legalDongName()).isEqualTo(LEGAL_DONG_NAME);
        assertThat(top.placeId()).isEqualTo(second);
    }

    // 법정동 합계와 최신 기록 시각까지 같으면 코드 오름차순으로 동을 선정한다.
    @Test
    void monthlyTopPlaceResolvesLegalDongTieByCode() {
        long userId = insertUser();
        long laterCode = insertPlace("1111010200", "나동", "-33.0000000", "-150.0000000");
        long earlierCode = insertPlace("1111010100", "가동", "-33.1000000", "-150.1000000");
        long track = insertTrack("monthly-dong-tie");
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        insertRecord(userId, laterCode, track, start.plusDays(2), null);
        insertRecord(userId, earlierCode, track, start.plusDays(2), null);

        MonthlyTopPlace top = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();

        assertThat(top.placeId()).isEqualTo(earlierCode);
        assertThat(top.legalDongName()).isEqualTo("가동");
    }

    // 미분류 장소도 하나의 동 그룹으로 합산해 기록 수가 많으면 선정한다.
    @Test
    void monthlyTopPlaceGroupsUnknownLegalDongRecords() {
        long userId = insertUser();
        long firstUnknown = insertPlace(null, null, "-33.0000000", "-150.0000000");
        long secondUnknown = insertPlace(null, null, "-33.1000000", "-150.1000000");
        long known = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.2000000", "-150.2000000");
        long track = insertTrack("monthly-unknown-dong");
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        insertRecord(userId, firstUnknown, track, start.plusDays(1), null);
        insertRecord(userId, secondUnknown, track, start.plusDays(2), null);
        insertRecord(userId, known, track, start.plusDays(3), null);

        MonthlyTopPlace top = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();

        assertThat(top.placeId()).isEqualTo(secondUnknown);
        assertThat(top.legalDongCode()).isNull();
        assertThat(top.legalDongName()).isNull();
    }

    // 같은 법정동 안에서 저장할 대표 장소 행은 개별 장소의 기록 수로 고른다.
    @Test
    void monthlyTopPlacePrioritizesPlaceCountOverNewerRecord() {
        long userId = insertUser();
        long frequent = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.0000000", "-150.0000000");
        long recent = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.1000000", "-150.1000000");
        long track = insertTrack("monthly-count");
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 0, 0);
        insertRecord(userId, frequent, track, start, null);
        insertRecord(userId, frequent, track, start.plusDays(1), null);
        insertRecord(userId, recent, track, start.plusDays(20), null);

        MonthlyTopPlace top = recordRepository.findMonthlyTopPlaces(
                userId, start, end, PageRequest.of(0, 1)).getFirst();
        assertThat(top.placeId()).isEqualTo(frequent);
    }

    // 상세 리포트 조회가 소유자를 제한하고 대표 장소를 함께 적재하는지 확인한다.
    @Test
    void monthlyReportDetailFetchesTopPlaceForOwner() {
        long userId = insertUser();
        long placeId = insertPlace(LEGAL_DONG_CODE, LEGAL_DONG_NAME,
                "-33.0000000", "-150.0000000");
        long reportId = insert("""
                INSERT INTO monthly_reports (user_id, report_year, report_month, record_count,
                    top_place_id, ai_recap_status) VALUES (?, ?, ?, ?, ?, ?)
                """, userId, 2026, 8, 1, placeId, "PENDING");
        entityManager.clear();

        var report = monthlyReportRepository
                .findByMonthlyReportIdAndUser_UserIdWithTopPlace(reportId, userId).orElseThrow();

        assertThat(Hibernate.isInitialized(report.getTopPlace())).isTrue();
        assertThat(report.getTopPlace().getLegalDongName()).isEqualTo(LEGAL_DONG_NAME);
        assertThat(monthlyReportRepository
                .findByMonthlyReportIdAndUser_UserIdWithTopPlace(reportId, -1L)).isEmpty();
    }

    @Test
    void myPlaceMarkersCountOnlyMyActiveInBoundsRecordsOncePerPlace() {
        long me = insertUser();
        long other = insertUser();
        long trackId = insertTrack("track");
        long inBounds = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long deletedOnly = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.1000000", "-150.1000000");
        long outOfBounds = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "10.0000000", "20.0000000");
        insertRecord(me, inBounds, trackId, FIRST_DAY, null);
        insertRecord(me, inBounds, trackId, FIRST_DAY.plusDays(1), null);
        insertRecord(me, inBounds, trackId, FIRST_DAY.plusDays(2), FIRST_DAY.plusDays(3));
        insertRecord(other, inBounds, trackId, FIRST_DAY, null);
        insertRecord(me, deletedOnly, trackId, FIRST_DAY, FIRST_DAY.plusDays(1));
        insertRecord(other, deletedOnly, trackId, FIRST_DAY, null);
        insertRecord(me, outOfBounds, trackId, FIRST_DAY, null);

        List<MyPlaceMarkerResponse> markers = recordRepository.findMyPlaceMarkersInBounds(
                me, SW_LAT, SW_LNG, NE_LAT, NE_LNG);

        assertThat(markers).hasSize(1);
        assertThat(markers.getFirst().placeId()).isEqualTo(inBounds);
        assertThat(markers.getFirst().myRecordsCount()).isEqualTo(2L);
        assertThat(recordRepository.findMyPlaceMarkersInBounds(
                -1L, SW_LAT, SW_LNG, NE_LAT, NE_LNG)).isEmpty();
    }

    @Test
    void myRecordsCursorDoesNotSkipOrRepeatRecordsWithSameCreatedAt() {
        long me = insertUser();
        long other = insertUser();
        long firstPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.0000000", "-150.0000000");
        long secondPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.1000000", "-150.1000000");
        long excludedPlace = insertPlace(
                LEGAL_DONG_CODE, LEGAL_DONG_NAME, "-33.2000000", "-150.2000000");
        long trackId = insertTrack("track");
        long oldest = insertRecord(me, firstPlace, trackId, FIRST_DAY, null);
        long sameTimeFirst = insertRecord(me, firstPlace, trackId, FIRST_DAY.plusDays(1), null);
        long sameTimeSecond = insertRecord(me, secondPlace, trackId, FIRST_DAY.plusDays(1), null);
        long sameTimeThird = insertRecord(me, firstPlace, trackId, FIRST_DAY.plusDays(1), null);
        long newest = insertRecord(me, secondPlace, trackId, FIRST_DAY.plusDays(2), null);
        insertRecord(me, firstPlace, trackId, FIRST_DAY.plusDays(3), FIRST_DAY.plusDays(4));
        insertRecord(other, firstPlace, trackId, FIRST_DAY.plusDays(4), null);
        insertRecord(me, excludedPlace, trackId, FIRST_DAY.plusDays(5), null);

        List<Long> placeIds = List.of(firstPlace, secondPlace);
        List<MyPlaceRecordResponseDto> first = recordRepository.findMyPlaceRecordsFirstPage(
                me, placeIds, PageRequest.of(0, 2));
        List<MyPlaceRecordResponseDto> second = recordRepository.findMyPlaceRecordsAfterCursor(
                me, placeIds, first.getLast().createdAt(), first.getLast().recordId(),
                PageRequest.of(0, 2));
        List<MyPlaceRecordResponseDto> third = recordRepository.findMyPlaceRecordsAfterCursor(
                me, placeIds, second.getLast().createdAt(), second.getLast().recordId(),
                PageRequest.of(0, 2));

        List<Long> ids = new ArrayList<>();
        first.forEach(row -> ids.add(row.recordId()));
        second.forEach(row -> ids.add(row.recordId()));
        third.forEach(row -> ids.add(row.recordId()));
        assertThat(ids).containsExactly(newest, sameTimeThird, sameTimeSecond, sameTimeFirst, oldest);
        assertThat(new HashSet<>(ids)).hasSize(5);
        assertThat(first.getFirst().musicTrackId()).isEqualTo(trackId);
        assertThat(first.getFirst().placeId()).isEqualTo(secondPlace);
        assertThat(first.getFirst().albumImageUrl()).isEqualTo("album");
        assertThat(second.getFirst().placeId()).isEqualTo(secondPlace);
        assertThat(second.getFirst().albumImageUrl()).isEqualTo("album");
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return insert("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                suffix + "@example.test", "test-hash", suffix);
    }

    private long insertPlace(
            String legalDongCode,
            String legalDongName,
            String latitude,
            String longitude
    ) {
        return insert("""
                INSERT INTO places (legal_dong_code, legal_dong_name, latitude, longitude)
                VALUES (?, ?, ?, ?)
                """, legalDongCode, legalDongName,
                new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private long insertTrack(String title) {
        return insertTrack(title, "artist");
    }

    private long insertTrack(String title, String artistName) {
        String externalId = UUID.randomUUID().toString().substring(0, 20);
        return insert("""
                INSERT INTO music_tracks (external_track_id, title, artist_name, album_image_url, external_url)
                VALUES (?, ?, ?, ?, ?)
                """, externalId, title, artistName, "album", "external");
    }

    private long insertRecord(
            long userId,
            long placeId,
            long musicTrackId,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        return insertRecord(userId, placeId, musicTrackId, (byte) 0, createdAt, deletedAt);
    }

    private long insertRecord(
            long userId,
            long placeId,
            long musicTrackId,
            byte moodScore,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        return insert("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score, created_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, placeId, musicTrackId, moodScore, createdAt, deletedAt);
    }

    private long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
