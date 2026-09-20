package com.ktb4.team16.mulo.weather.client;

import com.ktb4.team16.mulo.weather.client.dto.KmaVilageForecastResponse;
import com.ktb4.team16.mulo.weather.client.dto.KmaVilageForecastResponse.Item;
import com.ktb4.team16.mulo.weather.config.KmaWeatherProperties;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.KmaForecastMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KmaWeatherClient {
    private static final String PATH = "/getVilageFcst";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final KmaWeatherProperties properties;
    private final KmaForecastMapper mapper;

    @Autowired
    public KmaWeatherClient(KmaWeatherProperties properties, KmaForecastMapper mapper) {
        this(configuredBuilder(RestClient.builder(), REQUEST_TIMEOUT), properties, mapper);
    }

    KmaWeatherClient(RestClient.Builder builder, KmaWeatherProperties properties,
            KmaForecastMapper mapper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.mapper = mapper;
    }

    KmaWeatherClient(RestClient.Builder builder, KmaWeatherProperties properties,
            KmaForecastMapper mapper, Duration timeout) {
        this(configuredBuilder(builder, timeout), properties, mapper);
    }

    public List<ForecastSlot> fetch(GridCoordinate grid, ZonedDateTime baseAt) {
        try {
            KmaVilageForecastResponse payload = restClient.get()
                    .uri(buildUri(grid, baseAt))
                    .retrieve()
                    .body(KmaVilageForecastResponse.class);
            return parse(payload);
        } catch (RestClientException | IllegalArgumentException | DateTimeException exception) {
            throw new WeatherApiException(exception);
        }
    }

    private URI buildUri(GridCoordinate grid, ZonedDateTime baseAt) {
        // 중요: 공공데이터포털에서 받은 URL 인코딩 키를 다시 인코딩하지 않는다.
        String query = "?serviceKey=" + properties.serviceKey()
                + "&pageNo=1&numOfRows=1000&dataType=JSON"
                + "&base_date=" + DATE.format(baseAt)
                + "&base_time=" + TIME.format(baseAt)
                + "&nx=" + grid.x() + "&ny=" + grid.y();
        return URI.create(properties.baseUrl() + PATH + query);
    }

    private List<ForecastSlot> parse(KmaVilageForecastResponse payload) {
        if (payload == null || payload.response() == null
                || payload.response().header() == null
                || !"00".equals(payload.response().header().resultCode())) {
            throw new WeatherApiException();
        }
        if (payload.response().body() == null || payload.response().body().items() == null
                || payload.response().body().items().item() == null) {
            throw new WeatherApiException();
        }

        Map<LocalDateTime, Map<String, String>> grouped = new HashMap<>();
        for (Item item : payload.response().body().items().item()) {
            if (item == null || item.fcstDate() == null || item.fcstTime() == null
                    || item.category() == null || item.fcstValue() == null) {
                throw new WeatherApiException();
            }
            LocalDateTime forecastAt = LocalDateTime.of(
                    LocalDate.parse(item.fcstDate(), DATE),
                    LocalTime.parse(item.fcstTime(), TIME));
            grouped.computeIfAbsent(forecastAt, ignored -> new HashMap<>())
                    .put(item.category(), item.fcstValue());
        }

        if (grouped.values().stream().anyMatch(values ->
                !values.containsKey("TMP") || !values.containsKey("SKY")
                        || !values.containsKey("PTY"))) {
            throw new WeatherApiException();
        }

        return grouped.entrySet().stream()
                .map(entry -> new ForecastSlot(
                        entry.getKey(),
                        new BigDecimal(entry.getValue().get("TMP")),
                        mapper.toCondition(entry.getValue().get("PTY"),
                                entry.getValue().get("SKY"))))
                .sorted(Comparator.comparing(ForecastSlot::forecastAt))
                .toList();
    }

    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return builder.requestFactory(requestFactory);
    }
}
