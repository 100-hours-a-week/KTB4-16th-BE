package com.ktb4.team16.mulo.dailysong.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailySongRecordLocationRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailySongRecordLocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Coordinates> findLatestValid(long userId, LocalDateTime cutoff) {
        List<Coordinates> locations = jdbcTemplate.query("""
                SELECT p.latitude, p.longitude
                FROM records r
                JOIN places p ON p.place_id = r.place_id
                WHERE r.user_id = ? AND r.deleted_at IS NULL AND r.created_at <= ?
                    AND p.latitude BETWEEN -90 AND 90
                    AND p.longitude BETWEEN -180 AND 180
                ORDER BY r.created_at DESC, r.record_id DESC
                LIMIT 1
                """, (result, row) -> new Coordinates(result.getBigDecimal("latitude"),
                result.getBigDecimal("longitude")), userId, cutoff);
        return locations.stream().findFirst();
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
