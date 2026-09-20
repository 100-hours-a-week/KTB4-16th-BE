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
    private final Clock clock;

    public WeatherResponse getWeather(double latitude, double longitude,
            OffsetDateTime requestedAt) {
        ZonedDateTime requestedKst = requestedAt.atZoneSameInstant(KST);
        // 중요: 전송 지연을 고려해 현재 KST의 시간대는 허용하고, 완료된 이전 시간대만 차단한다.
        if (requestedKst.isBefore(ZonedDateTime.now(clock).truncatedTo(
                java.time.temporal.ChronoUnit.HOURS))) {
            throw new WeatherRequestTimeException();
        }

        GridCoordinate coordinate = coordinateConverter.convert(latitude, longitude);
        ZonedDateTime target = timePolicy.nearestForecastAt(requestedAt);
        LocalDate cacheDate = requestedKst.toLocalDate();

        Optional<WeatherGrid> existingGrid = findGrid(coordinate);
        Optional<WeatherGridForecast> cached = existingGrid.flatMap(grid ->
                findForecast(grid, cacheDate, target));
        if (cached.isPresent()) {
            return WeatherResponse.from(cached.get());
        }
        if (existingGrid.isPresent()
                && forecastRepository.existsByGridAndCacheDate(existingGrid.get(), cacheDate)) {
            // 중요: 당일 한 번 적재한 격자는 목표 슬롯이 없어도 외부 API를 다시 호출하지 않는다.
            throw new WeatherApiException();
        }

        for (ZonedDateTime baseAt : timePolicy.candidateBaseTimes(requestedKst, target)) {
            try {
                List<ForecastSlot> slots = weatherClient.fetch(coordinate, baseAt).stream()
                        .filter(slot -> !slot.forecastAt().isBefore(target.toLocalDateTime()))
                        .toList();
                if (slots.stream().noneMatch(slot ->
                        slot.forecastAt().equals(target.toLocalDateTime()))) {
                    continue;
                }
                cacheWriter.saveDailyForecasts(
                        coordinate, cacheDate, baseAt, ZonedDateTime.now(KST), slots);
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
