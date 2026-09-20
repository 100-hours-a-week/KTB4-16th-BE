package com.ktb4.team16.mulo.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.weather.config.KmaWeatherProperties;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.KmaForecastMapper;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaWeatherClientTest {
    private MockRestServiceServer server;
    private KmaWeatherClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KmaWeatherClient(builder,
                new KmaWeatherProperties("https://weather.test", "encoded-key"),
                new KmaForecastMapper());
    }

    @Test
    void groupsCategoriesByForecastTime() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("base_time=0800")))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        List<ForecastSlot> result = client.fetch(
                new GridCoordinate((short) 60, (short) 127),
                ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]"));

        assertThat(result).extracting(ForecastSlot::forecastAt)
                .containsExactly(
                        LocalDateTime.of(2026, 9, 20, 11, 0),
                        LocalDateTime.of(2026, 9, 20, 12, 0));
        assertThat(result.getFirst().temperature()).isEqualByComparingTo("25");
        assertThat(result.getFirst().weatherCondition().name()).isEqualTo("CLEAR");
        server.verify();
    }

    @Test
    void rejectsNoDataResultCode() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"},
                        "body":{"items":"","pageNo":1,"numOfRows":1000,"totalCount":0}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch(
                new GridCoordinate((short) 60, (short) 127),
                ZonedDateTime.parse("2026-09-20T11:00:00+09:00[Asia/Seoul]")))
                .isInstanceOf(WeatherApiException.class);
    }

    @Test
    void rejectsResponseWhenAnyForecastSlotMissesRequiredCategory() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess(incompleteSlotBody(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch(
                new GridCoordinate((short) 60, (short) 127),
                ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]")))
                .isInstanceOf(WeatherApiException.class);
    }

    @Test
    void convertsMalformedForecastDateToWeatherApiException() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess(successBody().replace("20260920", "invalid-date"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch(
                new GridCoordinate((short) 60, (short) 127),
                ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]")))
                .isInstanceOf(WeatherApiException.class);
    }

    @Test
    void timesOutSlowWeatherServer() throws Exception {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress(0), 0);
        slowServer.createContext("/getVilageFcst", exchange -> {
            try {
                Thread.sleep(500);
                byte[] body = successBody().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slowServer.start();
        try {
            KmaWeatherClient timeoutClient = new KmaWeatherClient(
                    RestClient.builder(),
                    new KmaWeatherProperties(
                            "http://localhost:" + slowServer.getAddress().getPort(), "key"),
                    new KmaForecastMapper(), Duration.ofMillis(50));

            assertThatThrownBy(() -> timeoutClient.fetch(
                    new GridCoordinate((short) 60, (short) 127),
                    ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]")))
                    .isInstanceOf(WeatherApiException.class);
        } finally {
            slowServer.stop(0);
        }
    }

    private String successBody() {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                "body":{"items":{"item":[
                {"baseDate":"20260920","baseTime":"0800","category":"TMP","fcstDate":"20260920","fcstTime":"1100","fcstValue":"25","nx":60,"ny":127},
                {"baseDate":"20260920","baseTime":"0800","category":"SKY","fcstDate":"20260920","fcstTime":"1100","fcstValue":"1","nx":60,"ny":127},
                {"baseDate":"20260920","baseTime":"0800","category":"PTY","fcstDate":"20260920","fcstTime":"1100","fcstValue":"0","nx":60,"ny":127},
                {"baseDate":"20260920","baseTime":"0800","category":"TMP","fcstDate":"20260920","fcstTime":"1200","fcstValue":"26","nx":60,"ny":127},
                {"baseDate":"20260920","baseTime":"0800","category":"SKY","fcstDate":"20260920","fcstTime":"1200","fcstValue":"3","nx":60,"ny":127},
                {"baseDate":"20260920","baseTime":"0800","category":"PTY","fcstDate":"20260920","fcstTime":"1200","fcstValue":"0","nx":60,"ny":127}
                ]},"pageNo":1,"numOfRows":1000,"totalCount":6}}}
                """;
    }

    private String incompleteSlotBody() {
        return successBody().replace(
                "{\"baseDate\":\"20260920\",\"baseTime\":\"0800\",\"category\":\"PTY\",\"fcstDate\":\"20260920\",\"fcstTime\":\"1200\",\"fcstValue\":\"0\",\"nx\":60,\"ny\":127}",
                "{\"baseDate\":\"20260920\",\"baseTime\":\"0800\",\"category\":\"REH\",\"fcstDate\":\"20260920\",\"fcstTime\":\"1200\",\"fcstValue\":\"40\",\"nx\":60,\"ny\":127}");
    }
}
