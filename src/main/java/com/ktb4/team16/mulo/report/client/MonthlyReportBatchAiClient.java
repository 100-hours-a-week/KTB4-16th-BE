package com.ktb4.team16.mulo.report.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.http.HttpClient;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MonthlyReportBatchAiClient {
    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public MonthlyReportBatchAiClient(AiProperties properties) {
        this(configuredBuilder(RestClient.builder(), properties), properties);
    }

    MonthlyReportBatchAiClient(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    // AI에 월간 리포트 배치 생성을 요청하고 접수된 작업 식별 정보를 반환한다.
    public BatchAccepted requestBatch(YearMonth targetMonth, List<Long> userIds) {
        try {
            BatchResponse response = restClient.post()
                    .uri(properties.baseUrl() + "/api/reports/batch-generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", properties.internalToken())
                    .body(new BatchRequest(targetMonth.getYear(), targetMonth.getMonthValue(), userIds))
                    .retrieve()
                    .body(BatchResponse.class);
            if (response == null || !"QUEUED".equals(response.status())
                    || response.jobId() == null || response.jobId().isBlank()
                    || response.targetCount() <= 0) {
                throw new MonthlyReportAiException();
            }
            return new BatchAccepted(response.jobId(), response.targetCount());
        } catch (MonthlyReportAiException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new MonthlyReportAiException(exception);
        }
    }

    // 기존 AI Client와 같은 연결·읽기 제한 시간을 HTTP 요청에 적용한다.
    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }

    private record BatchRequest(int year, int month, List<Long> userIds) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchResponse(String status, String jobId, int targetCount) { }

    public record BatchAccepted(String jobId, int targetCount) { }
}
