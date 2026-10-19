package com.ktb4.team16.mulo.memorysearch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpMemorySearchAiGatewayTest {

    /** 숫자 사용자 ID·이전 턴·내부 토큰을 전송하고 카드 대신 ID와 조건만 읽는다. */
    @Test
    void sendsContractAndPreservesOrderWhileIgnoringOldCardFields() {
        Fixture fixture = fixture("http://ai.test/ai/");
        fixture.server.expect(requestTo("http://ai.test/ai/api/memory-search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"userId":35,"query":"그중에 비 오는 날은?","history":[
                        {"query":"가을 노래","recordIds":[9,1]}]}
                        """))
                .andExpect(jsonPath("$.limit").doesNotExist())
                .andExpect(jsonPath("$.conversationId").doesNotExist())
                .andRespond(withSuccess("""
                        {"answer":"비 오는 날의 기억을 찾았어요.",
                        "records":[{"recordId":9,"title":"읽지 않는 제목"},{"recordId":1}],
                        "suggestedRecords":[{"recordId":2,"matchedConditions":["가을","공원 근처"],
                        "similarityScore":0.8}],"oldField":true}
                        """, MediaType.APPLICATION_JSON));

        MemorySearchAiResult result = fixture.gateway.search(35L, "그중에 비 오는 날은?",
                List.of(new MemorySearchTurn("가을 노래", List.of(9L, 1L))));

        assertThat(result.answer()).isEqualTo("비 오는 날의 기억을 찾았어요.");
        assertThat(result.recordIds()).containsExactly(9L, 1L);
        assertThat(result.suggestedRecords()).containsExactly(
                new MemorySearchAiResult.SuggestedRecord(2L, List.of("가을", "공원 근처")));
        fixture.server.verify();
    }

    /** 첫 질문과 결과 없음은 빈 배열을 그대로 사용하며 장애로 바꾸지 않는다. */
    @Test
    void sendsEmptyHistoryAndAcceptsEmptyResults() {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andExpect(content().json("{\"userId\":35,\"query\":\"가을\",\"history\":[]}"))
                .andRespond(withSuccess("""
                        {"answer":"다른 말로 다시 물어봐 주세요.","records":[],"suggestedRecords":[]}
                        """, MediaType.APPLICATION_JSON));

        MemorySearchAiResult result = fixture.gateway.search(35L, "가을", List.of());

        assertThat(result.answer()).isEqualTo("다른 말로 다시 물어봐 주세요.");
        assertThat(result.recordIds()).isEmpty();
        assertThat(result.suggestedRecords()).isEmpty();
        fixture.server.verify();
    }

    /** 정확 일치가 없어도 제안 조건을 손실 없이 응답할 수 있다. */
    @Test
    void acceptsSuggestionsWithoutExactMatches() {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withSuccess("""
                        {"answer":"모든 조건에 맞는 자물쇠는 없어요.","records":[],
                        "suggestedRecords":[{"recordId":4,"matchedConditions":["가을"]}]}
                        """, MediaType.APPLICATION_JSON));

        MemorySearchAiResult result = fixture.gateway.search(35L, "가을", List.of());

        assertThat(result.recordIds()).isEmpty();
        assertThat(result.suggestedRecords()).containsExactly(
                new MemorySearchAiResult.SuggestedRecord(4L, List.of("가을")));
    }

    /** 저장소 경계의 방어로 최근 5턴만 오래된 순서로 전달한다. */
    @Test
    void sendsOnlyFiveMostRecentCompletedTurns() {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andExpect(jsonPath("$.history.length()").value(5))
                .andExpect(jsonPath("$.history[0].query").value("질문3"))
                .andExpect(jsonPath("$.history[4].query").value("질문7"))
                .andRespond(withSuccess(validEmptyResult(), MediaType.APPLICATION_JSON));

        fixture.gateway.search(35L, "가을", IntStream.rangeClosed(1, 7)
                .mapToObj(index -> new MemorySearchTurn("질문" + index, List.of((long) index)))
                .toList());
        fixture.server.verify();
    }

    /** 50개까지의 ID를 받되 AI의 순서를 바꾸지 않는다. */
    @Test
    void acceptsFiftyExactResults() {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withSuccess("{\"answer\":\"기억을 찾았어요.\",\"records\":"
                        + exactRecords(50) + ",\"suggestedRecords\":[]}", MediaType.APPLICATION_JSON));

        assertThat(fixture.gateway.search(35L, "가을", List.of()).recordIds())
                .hasSize(50).startsWith(1L, 2L).endsWith(50L);
    }

    /** 필수 구조·ID·조건의 잘못된 형식은 정상 검색 결과로 공개하지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{not-json}",
            "{\"answer\":12,\"records\":[],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":null,\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[]}",
            "{\"answer\":\"a\",\"records\":[{}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":0}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":-1}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":1.5}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":\"abc\"}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":9223372036854775808}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[null],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[],\"suggestedRecords\":[{\"recordId\":1}]}",
            "{\"answer\":\"a\",\"records\":[],\"suggestedRecords\":[{\"recordId\":1,\"matchedConditions\":[null]}]}",
            "{\"answer\":\"a\",\"records\":[],\"suggestedRecords\":[{\"recordId\":1,\"matchedConditions\":[5]}]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":1},{\"recordId\":1}],\"suggestedRecords\":[]}",
            "{\"answer\":\"a\",\"records\":[{\"recordId\":1}],\"suggestedRecords\":[{\"recordId\":1,\"matchedConditions\":[]}]}",
            "{\"answer\":\"a\",\"records\":[],\"suggestedRecords\":[{\"recordId\":1,\"matchedConditions\":[]},{\"recordId\":1,\"matchedConditions\":[]}]}",
            "{\"message\":\"ok\",\"data\":{\"answer\":\"a\",\"records\":[],\"suggestedRecords\":[]}}"
    })
    void rejectsInvalidSuccessfulResponse(String body) {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertFailure(fixture, ErrorCode.AI_SERVICE_ERROR);
    }

    /** AI가 명세의 최대 결과 개수를 넘기면 임의로 자르지 않고 연동 오류로 처리한다. */
    @Test
    void rejectsOverLimitExactAndSuggestedArrays() {
        for (String body : List.of(
                "{\"answer\":\"a\",\"records\":" + exactRecords(51) + ",\"suggestedRecords\":[]}",
                """
                {"answer":"a","records":[],"suggestedRecords":[
                {"recordId":1,"matchedConditions":[]},{"recordId":2,"matchedConditions":[]},
                {"recordId":3,"matchedConditions":[]},{"recordId":4,"matchedConditions":[]}]}
                """)) {
            Fixture fixture = fixture("http://ai.test");
            fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertFailure(fixture, ErrorCode.AI_SERVICE_ERROR);
        }
    }

    /** AI의 400대는 사용자 입력 오류로 노출하지 않고 백엔드 내부 오류로 변환한다. */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429})
    void mapsAiClientErrorsToInternalServerError(int status) {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"INVALID_INPUT","field":"query","message":"sensitive-ai-detail"}
                        """));

        assertFailure(fixture, ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /** AI 장애·예상치 못한 HTTP 상태는 502로 처리하고 자동 재호출하지 않는다. */
    @ParameterizedTest
    @ValueSource(ints = {204, 302, 500, 502, 503})
    void mapsOtherInvalidStatusesToAiServiceError(int status) {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("sensitive-ai-detail"));

        assertFailure(fixture, ErrorCode.AI_SERVICE_ERROR);
    }

    /** 네트워크 실패도 외부 오류 메시지나 비밀값을 공개하지 않고 502로 처리한다. */
    @Test
    void mapsNetworkFailureToAiServiceError() {
        Fixture fixture = fixture("http://ai.test");
        fixture.server.expect(requestTo("http://ai.test/api/memory-search"))
                .andRespond(withException(new IOException("sensitive-ai-detail")));

        assertFailure(fixture, ErrorCode.AI_SERVICE_ERROR);
    }

    /** 토큰이 비어 있으면 비인증 HTTP 호출을 보내지 않고 설정 오류로 처리한다. */
    @Test
    void rejectsMissingTokenWithoutCallingAi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("http://ai.test"), "",
                Duration.ofSeconds(5), Duration.ofSeconds(30), true);
        HttpMemorySearchAiGateway gateway = new HttpMemorySearchAiGateway(builder.build(), properties);

        assertThatThrownBy(() -> gateway.search(35L, "가을", List.of()))
                .isInstanceOfSatisfying(MemorySearchDomainException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        server.verify();
    }

    /** 공개 예외는 HTTP 내부 본문을 보관하거나 사용자 메시지로 노출하지 않는다. */
    private void assertFailure(Fixture fixture, ErrorCode expected) {
        assertThatThrownBy(() -> fixture.gateway.search(35L, "가을", List.of()))
                .isInstanceOfSatisfying(MemorySearchDomainException.class, failure -> {
                    assertThat(failure.errorCode()).isEqualTo(expected);
                    assertThat(failure.getMessage()).doesNotContain("sensitive-ai-detail", "test-internal-token");
                });
        fixture.server.verify();
    }

    /** 외부 서버만 대체하고 실제 RestClient JSON 변환·요청·응답 경계를 검증한다. */
    private Fixture fixture(String baseUrl) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create(baseUrl), "test-internal-token",
                Duration.ofSeconds(5), Duration.ofSeconds(30), false);
        return new Fixture(server, new HttpMemorySearchAiGateway(builder.build(), properties));
    }

    /** 명세의 정상 빈 결과 JSON을 준비한다. */
    private String validEmptyResult() {
        return "{\"answer\":\"없어요.\",\"records\":[],\"suggestedRecords\":[]}";
    }

    /** 개수 경계 검증용 ID 배열을 입력 개수만큼 만든다. */
    private String exactRecords(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id -> "{\"recordId\":" + id + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private record Fixture(MockRestServiceServer server, HttpMemorySearchAiGateway gateway) { }
}
