package com.ktb4.team16.mulo.memorysearch.ai;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 AI 기억 검색 API를 호출하고 외부 JSON을 검증된 ID 결과로 변환한다. */
@Component
public class HttpMemorySearchAiGateway implements MemorySearchAiGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpMemorySearchAiGateway.class);
    private static final String SEARCH_ENDPOINT = "/api/memory-search";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final Set<String> KNOWN_ERROR_CODES = Set.of(
            "INVALID_INPUT", "UNAUTHORIZED", "UPSTREAM_UNAVAILABLE");
    private static final Set<String> KNOWN_ERROR_FIELDS = Set.of("userId", "query", "limit", "history");

    private final RestClient restClient;
    private final AiProperties properties;
    private final JsonMapper diagnosticMapper = JsonMapper.builder().build();

    /** 기억 검색 전용 HTTP 클라이언트와 기존 AI 주소·인증 설정을 연결한다. */
    public HttpMemorySearchAiGateway(@Qualifier("memorySearchRestClient") RestClient restClient,
            AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 완료된 이전 5턴을 전달하고 HTTP·계약 오류를 공개 도메인 오류로 구분한다. */
    @Override
    public MemorySearchAiResult search(Long userId, String query, List<MemorySearchTurn> history) {
        if (properties.internalToken() == null || properties.internalToken().isBlank()) {
            log.error("Memory search AI configuration failure: missing internal token");
            throw new MemorySearchDomainException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        List<MemorySearchTurn> recentHistory = List.copyOf(history.subList(
                Math.max(0, history.size() - MemorySearchLimits.MAX_RECENT_TURNS), history.size()));
        try {
            // 프록시 경로가 포함된 base URL도 보존하며 끝의 '/'만 정리한다.
            String baseUrl = properties.baseUrl().toString().replaceAll("/+$", "");
            var response = restClient.post()
                    .uri(baseUrl + SEARCH_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.internalToken())
                    .body(new SearchRequest(userId, query, recentHistory))
                    .retrieve()
                    .toEntity(JsonNode.class);
            if (response.getStatusCode().value() != 200) {
                throw invalidResponse();
            }
            return parseResponse(response.getBody());
        } catch (MemorySearchDomainException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            // 예외 메시지에는 URL·내부 정보가 포함될 수 있어 원문이나 스택을 기록하지 않는다.
            log.warn("Memory search AI communication failure (connection or read timeout)");
            throw new MemorySearchDomainException(ErrorCode.AI_SERVICE_ERROR);
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    /** JSON 타입을 확인해 숫자·불리언의 문자열 강제 변환과 소수 ID 잘림을 막는다. */
    private MemorySearchAiResult parseResponse(JsonNode body) {
        if (body == null || !body.isObject() || !body.path("answer").isTextual()
                || !body.path("records").isArray() || !body.path("suggestedRecords").isArray()) {
            throw invalidResponse();
        }
        JsonNode records = body.get("records");
        JsonNode suggestions = body.get("suggestedRecords");
        if (records.size() > MemorySearchLimits.MAX_RESULT_RECORDS
                || suggestions.size() > MemorySearchLimits.MAX_SUGGESTED_RECORDS
                || records.size() + suggestions.size() > MemorySearchLimits.MAX_RESULT_RECORDS) {
            throw invalidResponse();
        }
        Set<Long> seenIds = new HashSet<>();
        List<Long> recordIds = new ArrayList<>();
        for (JsonNode record : records) {
            recordIds.add(readRecordId(record, seenIds));
        }
        List<MemorySearchAiResult.SuggestedRecord> suggestedRecords = new ArrayList<>();
        for (JsonNode record : suggestions) {
            Long recordId = readRecordId(record, seenIds);
            JsonNode conditions = record.path("matchedConditions");
            if (!conditions.isArray()) {
                throw invalidResponse();
            }
            List<String> matchedConditions = new ArrayList<>();
            for (JsonNode condition : conditions) {
                if (!condition.isTextual()) {
                    throw invalidResponse();
                }
                matchedConditions.add(condition.asText());
            }
            suggestedRecords.add(new MemorySearchAiResult.SuggestedRecord(recordId, matchedConditions));
        }
        // 구 카드·점수 필드는 읽지 않고 기존 DB 카드 변환에 ID만 전달한다.
        return new MemorySearchAiResult(body.get("answer").asText(), recordIds, suggestedRecords);
    }

    /** 양의 Long ID만 허용하고 배열 내부·사이의 중복 ID는 계약 위반으로 처리한다. */
    private Long readRecordId(JsonNode record, Set<Long> seenIds) {
        JsonNode id = record.path("recordId");
        if (!record.isObject() || !id.isIntegralNumber() || !id.canConvertToLong()
                || id.asLong() <= 0 || !seenIds.add(id.asLong())) {
            throw invalidResponse();
        }
        return id.asLong();
    }

    /** AI의 400대는 내부 계약·설정 오류, 나머지 HTTP 장애는 502로 변환한다. */
    private MemorySearchDomainException mapHttpFailure(RestClientResponseException exception) {
        String code = "UNKNOWN";
        String field = "NONE";
        try {
            JsonNode body = diagnosticMapper.readTree(exception.getResponseBodyAsByteArray());
            if (body != null) {
                code = recognizedValue(body.path("code"), KNOWN_ERROR_CODES, "UNKNOWN");
                field = recognizedValue(body.path("field"), KNOWN_ERROR_FIELDS, "NONE");
            }
        } catch (RuntimeException ignored) {
            // JSON이 아닌 장애 본문도 공개·기록하지 않고 HTTP 상태만으로 처리한다.
        }
        log.warn("Memory search AI HTTP failure: status={}, code={}, field={}",
                exception.getStatusCode().value(), code, field);
        return new MemorySearchDomainException(exception.getStatusCode().is4xxClientError()
                ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.AI_SERVICE_ERROR);
    }

    /** AI의 임의 문자열을 로그에 넣지 않고 알려진 진단 값만 추출한다. */
    private String recognizedValue(JsonNode value, Set<String> allowed, String fallback) {
        return value.isTextual() && allowed.contains(value.asText()) ? value.asText() : fallback;
    }

    /** 사용자에게 내부 본문을 보이지 않고 성공 응답의 계약 위반을 502로 반환한다. */
    private MemorySearchDomainException invalidResponse() {
        log.warn("Memory search AI response does not match the JSON contract");
        return new MemorySearchDomainException(ErrorCode.AI_SERVICE_ERROR);
    }

    private record SearchRequest(Long userId, String query, List<MemorySearchTurn> history) { }
}
