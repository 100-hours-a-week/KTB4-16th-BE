package com.ktb4.team16.mulo.place.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException.Reason;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoRegionClientTests {

    private static final BigDecimal LATITUDE = new BigDecimal("37.4012191");
    private static final BigDecimal LONGITUDE = new BigDecimal("127.1086228");
    private static final String REQUEST_URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"
            + "?x=127.1086228&y=37.4012191";

    @Test
    void selectsLegalRegionAndSendsLongitudeAsX() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(REQUEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK " + fixture.credential))
                .andRespond(withSuccess("""
                        {"documents":[
                          {"region_type":"H","code":"H-CODE","address_name":"행정동",
                           "region_3depth_name":"역삼1동"},
                          {"region_type":"B","code":"B-CODE","address_name":"법정동",
                           "region_1depth_name":"시도","region_2depth_name":"시군구",
                           "region_3depth_name":"역삼동","region_4depth_name":"리"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        String regionName = fixture.client.findLegalRegionName(LATITUDE, LONGITUDE);

        assertEquals("역삼동", regionName);
        fixture.server.verify();
    }

    @Test
    void rejectsResponseWithoutLegalRegion() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("""
                        {"documents":[{"region_type":"H","code":"H-CODE"}]}
                        """, MediaType.APPLICATION_JSON));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.LEGAL_REGION_NOT_FOUND, exception.getReason());
        fixture.server.verify();
    }

    @Test
    void distinguishesHttpFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.API_ERROR, exception.getReason());
        fixture.server.verify();
    }

    @Test
    void distinguishesServerFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.API_ERROR, exception.getReason());
        fixture.server.verify();
    }

    @Test
    void rejectsMalformedResponse() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.INVALID_RESPONSE, exception.getReason());
        fixture.server.verify();
    }

    @Test
    void rejectsLegalRegionWithoutName() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("""
                        {"documents":[{"region_type":"B","region_3depth_name":" "}]}
                        """, MediaType.APPLICATION_JSON));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.LEGAL_REGION_NOT_FOUND, exception.getReason());
        fixture.server.verify();
    }

    @Test
    void rejectsLegalRegionWithMissingName() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("""
                        {"documents":[{"region_type":"B"}]}
                        """, MediaType.APPLICATION_JSON));

        var exception = assertThrows(KakaoRegionLookupException.class,
                () -> fixture.client.findLegalRegionName(LATITUDE, LONGITUDE));

        assertEquals(Reason.LEGAL_REGION_NOT_FOUND, exception.getReason());
        fixture.server.verify();
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String credential = UUID.randomUUID().toString();
        KakaoRegionClient client = new KakaoRegionClient(builder, credential);
        return new Fixture(server, client, credential);
    }

    private record Fixture(MockRestServiceServer server, KakaoRegionClient client,
            String credential) {
    }
}
