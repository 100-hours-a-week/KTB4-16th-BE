package com.ktb4.team16.mulo.record.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.place.dto.PopularPlaceMarkerResponseDto;
import com.ktb4.team16.mulo.place.dto.PopularTrackAggregateDto;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.MyPlaceMarkerResponseDto;
import com.ktb4.team16.mulo.record.dto.MyPlaceRecordResponseDto;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void popularPlacesAreDistinctAndExcludeDeletedAndOutOfBoundsRecords() {
        long userId = insertUser();
        long trackId = insertTrack("track");
        long inBounds = insertPlace(null, "테스트동", "-33.0000000", "-150.0000000");
        long deletedOnly = insertPlace("deleted", "테스트동", "-33.1000000", "-150.1000000");
        long outOfBounds = insertPlace("outside", "테스트동", "10.0000000", "20.0000000");
        insertRecord(userId, inBounds, trackId, FIRST_DAY, null);
        insertRecord(userId, inBounds, trackId, FIRST_DAY.plusDays(1), null);
        insertRecord(userId, deletedOnly, trackId, FIRST_DAY, FIRST_DAY.plusDays(1));
        insertRecord(userId, outOfBounds, trackId, FIRST_DAY, null);

        List<PopularPlaceMarkerResponseDto> markers = recordRepository.findPopularPlacesInBounds(
                SW_LAT, SW_LNG, NE_LAT, NE_LNG);

        assertThat(markers).hasSize(1);
        assertThat(markers.getFirst().getPlaceId()).isEqualTo(inBounds);
        assertThat(markers.getFirst().getPlaceName()).isNull();
        assertThat(markers.getFirst().getDongName()).isEqualTo("테스트동");
        assertThat(markers.getFirst().getLatitude()).isEqualByComparingTo("-33.0000000");
        assertThat(placeRepository.findById(deletedOnly)).isPresent();
    }

    @Test
    void popularTracksUseCountThenLatestRecordThenTrackIdOrder() {
        long userId = insertUser();
        long firstPlace = insertPlace("popular-first", "테스트동", "-33.0000000", "-150.0000000");
        long secondPlace = insertPlace("popular-second", "테스트동", "-33.1000000", "-150.1000000");
        long excludedPlace = insertPlace("excluded", "테스트동", "-33.2000000", "-150.2000000");
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

        assertThat(tracks).extracting(PopularTrackAggregateDto::getMusicTrackId)
                .containsExactly(mostCount, tiedFirst, tiedSecond, older);
        assertThat(tracks).extracting(PopularTrackAggregateDto::getCount)
                .containsExactly(3L, 2L, 2L, 2L);
        assertThat(tracks.get(1).getLatestRecordCreatedAt()).isEqualTo(FIRST_DAY.plusDays(9));
        assertThat(recordRepository.countActiveRecordsAtPlaces(placeIds, createdAtFrom)).isEqualTo(9L);
    }

    @Test
    void myPlaceMarkersCountOnlyMyActiveInBoundsRecordsOncePerPlace() {
        long me = insertUser();
        long other = insertUser();
        long trackId = insertTrack("track");
        long inBounds = insertPlace("mine", "테스트동", "-33.0000000", "-150.0000000");
        long deletedOnly = insertPlace("deleted", "테스트동", "-33.1000000", "-150.1000000");
        long outOfBounds = insertPlace("outside", "테스트동", "10.0000000", "20.0000000");
        insertRecord(me, inBounds, trackId, FIRST_DAY, null);
        insertRecord(me, inBounds, trackId, FIRST_DAY.plusDays(1), null);
        insertRecord(me, inBounds, trackId, FIRST_DAY.plusDays(2), FIRST_DAY.plusDays(3));
        insertRecord(other, inBounds, trackId, FIRST_DAY, null);
        insertRecord(me, deletedOnly, trackId, FIRST_DAY, FIRST_DAY.plusDays(1));
        insertRecord(other, deletedOnly, trackId, FIRST_DAY, null);
        insertRecord(me, outOfBounds, trackId, FIRST_DAY, null);

        List<MyPlaceMarkerResponseDto> markers = recordRepository.findMyPlaceMarkersInBounds(
                me, SW_LAT, SW_LNG, NE_LAT, NE_LNG);

        assertThat(markers).hasSize(1);
        assertThat(markers.getFirst().getPlaceId()).isEqualTo(inBounds);
        assertThat(markers.getFirst().getMyRecordsCount()).isEqualTo(2L);
        assertThat(recordRepository.findMyPlaceMarkersInBounds(
                -1L, SW_LAT, SW_LNG, NE_LAT, NE_LNG)).isEmpty();
    }

    @Test
    void myRecordsCursorDoesNotSkipOrRepeatRecordsWithSameCreatedAt() {
        long me = insertUser();
        long other = insertUser();
        long firstPlace = insertPlace("mine-first", "테스트동", "-33.0000000", "-150.0000000");
        long secondPlace = insertPlace("mine-second", "테스트동", "-33.1000000", "-150.1000000");
        long excludedPlace = insertPlace("excluded", "테스트동", "-33.2000000", "-150.2000000");
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
                me, placeIds, first.getLast().getCreatedAt(), first.getLast().getRecordId(),
                PageRequest.of(0, 2));
        List<MyPlaceRecordResponseDto> third = recordRepository.findMyPlaceRecordsAfterCursor(
                me, placeIds, second.getLast().getCreatedAt(), second.getLast().getRecordId(),
                PageRequest.of(0, 2));

        List<Long> ids = new ArrayList<>();
        first.forEach(row -> ids.add(row.getRecordId()));
        second.forEach(row -> ids.add(row.getRecordId()));
        third.forEach(row -> ids.add(row.getRecordId()));
        assertThat(ids).containsExactly(newest, sameTimeThird, sameTimeSecond, sameTimeFirst, oldest);
        assertThat(new HashSet<>(ids)).hasSize(5);
        assertThat(first.getFirst().getMusicTrackId()).isEqualTo(trackId);
        assertThat(first.getFirst().getPlaceId()).isEqualTo(secondPlace);
        assertThat(second.getFirst().getPlaceId()).isEqualTo(secondPlace);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return insert("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                suffix + "@example.test", "test-hash", suffix);
    }

    private long insertPlace(String name, String dongName, String latitude, String longitude) {
        return insert("""
                INSERT INTO places (place_name, dong_name, latitude, longitude)
                VALUES (?, ?, ?, ?)
                """, name, dongName, new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private long insertTrack(String title) {
        String externalId = UUID.randomUUID().toString().substring(0, 20);
        return insert("""
                INSERT INTO music_tracks (external_track_id, title, artist_name, album_image_url, external_url)
                VALUES (?, ?, ?, ?, ?)
                """, externalId, title, "artist", "album", "external");
    }

    private long insertRecord(
            long userId,
            long placeId,
            long musicTrackId,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        return insert("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score, created_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, placeId, musicTrackId, 0, createdAt, deletedAt);
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
