package com.ktb4.team16.mulo.report.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.URI;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MonthlyReportBatchAiClientTest {

    @Test
    void requestsBatchWithTokenAndMapsQueuedResponse() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://ai.test/api/reports/batch-generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(content().json("{\"year\":2026,\"month\":9,\"userIds\":[7,12]}"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"QUEUED\",\"jobId\":\"report_batch_2026-9\",\"targetCount\":2}"));

        var accepted = fixture.client.requestBatch(YearMonth.of(2026, 9), List.of(7L, 12L));

        assertThat(accepted.jobId()).isEqualTo("report_batch_2026-9");
        assertThat(accepted.targetCount()).isEqualTo(2);
        fixture.server.verify();
    }

    @Test
    void rejectsNonAcceptedResponseWithoutExposingAiBody() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://ai.test/api/reports/batch-generate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("sensitive-ai-detail"));

        assertThatThrownBy(() -> fixture.client.requestBatch(YearMonth.of(2026, 9), List.of(7L)))
                .isInstanceOf(MonthlyReportAiException.class)
                .hasMessageNotContaining("sensitive-ai-detail");
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("http://ai.test"), "test-token",
                Duration.ofSeconds(1), Duration.ofSeconds(1), false);
        return new Fixture(server, new MonthlyReportBatchAiClient(builder, properties));
    }

    private record Fixture(MockRestServiceServer server, MonthlyReportBatchAiClient client) { }
}
