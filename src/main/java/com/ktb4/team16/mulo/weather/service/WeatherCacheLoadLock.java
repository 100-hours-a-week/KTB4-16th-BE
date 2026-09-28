package com.ktb4.team16.mulo.weather.service;

import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherCacheLoadLock {
    private static final int LOCK_WAIT_SECONDS = 10;
    private final DataSource dataSource;

    // 같은 격자·날짜의 캐시 적재를 하나의 MySQL session에서 직렬화한다.
    public <T> T withLock(GridCoordinate coordinate, LocalDate cacheDate, Supplier<T> action) {
        String lockName = lockName(coordinate, cacheDate);
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, lockName)) {
                throw new WeatherApiException();
            }
            try {
                return action.get();
            } finally {
                release(connection, lockName);
            }
        } catch (SQLException exception) {
            throw new WeatherApiException(exception);
        }
    }

    // MySQL named lock을 제한 시간 안에 얻을 수 있는지 확인한다.
    private boolean acquire(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, LOCK_WAIT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1 && !result.wasNull();
            }
        }
    }

    // 작업 성공·실패와 무관하게 동일 session에서 named lock 해제를 시도한다.
    private void release(Connection connection, String lockName) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery();
        } catch (SQLException ignored) {
            // Connection 종료 시 MySQL이 남은 named lock을 해제한다.
        }
    }

    // 잠금 범위를 격자와 KST 캐시 날짜로 제한해 다른 지역 요청의 병렬 처리를 보장한다.
    private String lockName(GridCoordinate coordinate, LocalDate cacheDate) {
        return "mulo.weather-cache:" + coordinate.x() + ":" + coordinate.y() + ":" + cacheDate;
    }
}
