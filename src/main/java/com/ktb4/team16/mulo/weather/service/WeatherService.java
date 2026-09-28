package com.ktb4.team16.mulo.weather.service;

import com.ktb4.team16.mulo.weather.client.KmaWeatherClient;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.exception.WeatherRequestTimeException;
import com.ktb4.team16.mulo.weather.repository.WeatherGridForecastRepository;
import com.ktb4.team16.mulo.weather.repository.WeatherGridRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GridCoordinateConverter coordinateConverter;
    private final WeatherTimePolicy timePolicy;
    private final WeatherGridRepository gridRepository;
    private final WeatherGridForecastRepository forecastRepository;
    private final KmaWeatherClient weatherClient;
    private final WeatherCacheWriter cacheWriter;
    private final WeatherCacheLoadLock cacheLoadLock;
    private final Clock clock;

    // 서버 시각으로 캐시를 조회하고, 미스면 격자별 단일 적재 작업으로 예보를 확보한다.
    public WeatherResponse getWeather(double latitude, double longitude,
            OffsetDateTime requestedAt) {
        ZonedDateTime requestedKst = requestedAt.atZoneSameInstant(KST);
        ZonedDateTime serverNow = ZonedDateTime.now(clock).withZoneSameInstant(KST);
        // 중요: 전송 지연을 고려해 현재 KST의 시간대는 허용하고, 완료된 이전 시간대만 차단한다.
        if (requestedKst.isBefore(serverNow.truncatedTo(
                java.time.temporal.ChronoUnit.HOURS))) {
            throw new WeatherRequestTimeException();
        }

        ZonedDateTime effectiveAt = requestedKst.isAfter(serverNow) ? serverNow : requestedKst;
        GridCoordinate coordinate = coordinateConverter.convert(latitude, longitude);
        ZonedDateTime target = timePolicy.nearestForecastAt(effectiveAt.toOffsetDateTime());
        LocalDate cacheDate = effectiveAt.toLocalDate();

        Optional<WeatherGrid> existingGrid = findGrid(coordinate);
        Optional<WeatherGridForecast> cached = existingGrid.flatMap(grid ->
                findForecast(grid, cacheDate, target));
        if (cached.isPresent()) {
            return WeatherResponse.from(cached.get());
        }
        return cacheLoadLock.withLock(coordinate, cacheDate,
                () -> loadMissingForecast(coordinate, cacheDate, target, effectiveAt, serverNow));
    }

    // lock 획득 뒤 캐시를 다시 확인하고, 여전히 없을 때만 KMA를 최대 두 회차 호출한다.
    private WeatherResponse loadMissingForecast(GridCoordinate coordinate, LocalDate cacheDate,
            ZonedDateTime target, ZonedDateTime effectiveAt, ZonedDateTime serverNow) {
        Optional<WeatherGrid> existingGrid = findGrid(coordinate);
        Optional<WeatherGridForecast> cached = existingGrid.flatMap(grid ->
                findForecast(grid, cacheDate, target));
        if (cached.isPresent()) {
            return WeatherResponse.from(cached.get());
        }
        boolean hasDailyCache = existingGrid.isPresent()
                && forecastRepository.existsByGridAndCacheDate(existingGrid.get(), cacheDate);

        for (ZonedDateTime baseAt : timePolicy.candidateBaseTimes(effectiveAt, target)) {
            try {
                List<ForecastSlot> slots = weatherClient.fetch(coordinate, baseAt).stream()
                        .filter(slot -> !slot.forecastAt().isBefore(target.toLocalDateTime()))
                        .toList();
                if (slots.stream().noneMatch(slot ->
                        slot.forecastAt().equals(target.toLocalDateTime()))) {
                    continue;
                }
                if (hasDailyCache) {
                    ForecastSlot targetSlot = slots.stream()
                            .filter(slot -> slot.forecastAt().equals(target.toLocalDateTime()))
                            .findFirst().orElseThrow(WeatherApiException::new);
                    cacheWriter.saveMissingForecast(existingGrid.orElseThrow(), cacheDate,
                            baseAt, serverNow, targetSlot);
                } else {
                    cacheWriter.saveDailyForecasts(coordinate, cacheDate, baseAt, serverNow, slots);
                }
                return findStored(coordinate, cacheDate, target);
            } catch (DataIntegrityViolationException exception) {
                return findStored(coordinate, cacheDate, target);
            } catch (WeatherApiException exception) {
                // 허용된 다음 후보 회차를 한 번 더 시도한다.
            }
        }
        throw new WeatherApiException();
    }

    private Optional<WeatherGrid> findGrid(GridCoordinate coordinate) {
        return gridRepository.findByGridXAndGridY(coordinate.x(), coordinate.y());
    }

    private Optional<WeatherGridForecast> findForecast(WeatherGrid grid,
            LocalDate cacheDate, ZonedDateTime target) {
        return forecastRepository.findByGridAndCacheDateAndForecastAt(
                grid, cacheDate, target.toLocalDateTime());
    }

    private WeatherResponse findStored(GridCoordinate coordinate, LocalDate cacheDate,
            ZonedDateTime target) {
        WeatherGrid grid = findGrid(coordinate).orElseThrow(WeatherApiException::new);
        WeatherGridForecast forecast = findForecast(grid, cacheDate, target)
                .orElseThrow(WeatherApiException::new);
        return WeatherResponse.from(forecast);
    }
}
