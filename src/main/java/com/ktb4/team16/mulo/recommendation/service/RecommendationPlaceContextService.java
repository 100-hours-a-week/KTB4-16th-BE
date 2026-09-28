package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationPlaceContextService {
    private final KakaoRegionClient kakaoRegionClient;

    // 법정동 조회의 예상 가능한 실패만 빈 선택 문맥으로 바꿔 추천 생성을 계속하게 한다.
    public Optional<String> findLegalDongName(BigDecimal latitude, BigDecimal longitude) {
        try {
            return Optional.of(kakaoRegionClient.findLegalRegionName(latitude, longitude));
        } catch (KakaoRegionLookupException exception) {
            return Optional.empty();
        }
    }
}
