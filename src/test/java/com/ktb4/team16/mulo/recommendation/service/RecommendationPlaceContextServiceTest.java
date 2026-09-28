package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaceContextServiceTest {
    @Mock private KakaoRegionClient kakaoRegionClient;

    // 법정동 조회가 성공하면 AI place.name에 사용할 이름을 반환한다.
    @Test
    void returnsLegalDongNameWhenLookupSucceeds() {
        when(kakaoRegionClient.findLegalRegionName(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)))
                .thenReturn("태평로1가");

        assertThat(service().findLegalDongName(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)))
                .contains("태평로1가");
    }

    // 법정동 조회 실패는 추천을 중단하지 않도록 빈 값으로 바꾼다.
    @Test
    void returnsEmptyWhenLookupFails() {
        when(kakaoRegionClient.findLegalRegionName(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)))
                .thenThrow(new KakaoRegionLookupException(KakaoRegionLookupException.Reason.API_ERROR));

        assertThat(service().findLegalDongName(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780))).isEmpty();
    }

    // 법정동 조회 전용 서비스를 조립한다.
    private RecommendationPlaceContextService service() {
        return new RecommendationPlaceContextService(kakaoRegionClient);
    }
}
