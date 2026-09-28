# AI 추천 플레이리스트 주변 자물쇠 문맥 설계

## 목적

AI 상황 맞춤 추천(`POST /api/context-recommend`)에 선택 문맥인 `nearbyTracks`를 전달한다.
사용자가 홈 지도에서 인기 장소 마커 또는 클러스터를 선택한 경우에만, 선택된 장소들의 최근 인기 자물쇠 음악을 AI 추천에 반영한다.

## 확정된 범위

- AI 요청의 `place` 객체는 전송하지 않는다.
- 프론트는 지도에서 선택된 인기 마커 또는 클러스터가 있을 때만 `placeIds`를 추천 생성 API에 선택적으로 보낸다.
- 선택이 없으면 `placeIds`와 AI의 `nearbyTracks`를 모두 생략한다.
- 백엔드는 전달된 `placeIds`를 기준으로 기존 인기 음악 집계 규칙을 재사용하고, 상위 5곡만 AI에 보낸다.
- 법정동 코드/이름은 이번 추천 요청 계약에 추가하지 않는다. 자물쇠 생성과 대시보드의 기존 법정동 정책은 변경하지 않는다.
- `requestId`는 동기식 추천 저장에 필요하지 않으므로 이번 범위에서 전송·저장하지 않는다.

## 요청 흐름

```text
홈 지도 인기 마커/클러스터 선택
  -> 프론트가 선택된 placeIds 유지
  -> POST /api/recommendations/playlists 에 선택적으로 placeIds 전달
  -> 백엔드가 날씨 조회
  -> 백엔드가 placeIds의 최근 7일 활성 자물쇠 음악을 집계
  -> 상위 5곡을 nearbyTracks로 변환
  -> POST /api/context-recommend 호출
  -> 정상 AI 추천 결과를 현재 플레이리스트로 저장
```

## 프론트-백엔드 계약

### 기존 요청

```json
{
  "latitude": 37.4005578,
  "longitude": 127.1069625
}
```

### 변경 요청

```json
{
  "latitude": 37.4005578,
  "longitude": 127.1069625,
  "placeIds": [10, 20, 30]
}
```

`placeIds`는 선택 필드다. 프론트는 선택된 장소가 없을 때 빈 배열을 보내지 않고 필드를 생략한다. 백엔드는 수신한 경우 양의 정수 ID 목록으로 검증한다.

## 백엔드-AI 계약

선택 장소에 최근 7일 활성 자물쇠 음악이 존재하면 다음 필드를 추가한다.

```json
{
  "userId": 7,
  "weather": {
    "condition": "CLEAR",
    "temperature": 23.0
  },
  "localTime": "2026-09-28T14:00:00+09:00",
  "nearbyTracks": [
    {
      "title": "비도 오고 그래서",
      "artistName": "헤이즈",
      "count": 9
    }
  ],
  "limit": 10
}
```

선택 장소가 없거나 집계 결과가 비어 있으면 `nearbyTracks`를 생략한다. 이는 AI 계약에서 허용하는 선택 필드 처리다.

## 집계 규칙

기존 `POST /api/places/popular-tracks/search`의 정책을 추천 문맥에도 사용한다.

- 대상: 전달된 모든 `placeIds`에 속한 자물쇠
- 기간: 조회 시점 기준 최근 7일
- 제외: 삭제된 자물쇠
- 정렬: 음악별 자물쇠 수 내림차순, 동률이면 가장 최근 자물쇠 생성 시각 내림차순, 이후 음악 ID 오름차순
- AI 전달: 정렬 결과의 앞 5곡

기존 집계는 `RecordRepository.findPopularTrackAggregates`가 담당한다. 추천 서비스는 이 조회 결과를 AI 전송 DTO로 변환하며, 내부 HTTP API를 다시 호출하지 않는다.

## 오류 처리 및 저장

- `placeIds`가 없거나 해당 장소에 최근 자물쇠가 없어도 추천 생성은 실패하지 않는다. `nearbyTracks` 없이 AI를 호출한다.
- 날씨 조회 실패는 기존 정책대로 `502 WEATHER_API_ERROR`이며 AI 호출 전 중단된다.
- AI가 `degraded=true` 또는 빈 곡 목록을 반환하면 기존 플레이리스트를 유지한다.
- `placeIds` 유효성 오류는 추천 생성 API의 입력 검증 오류로 처리한다.

## 테스트 범위

- 프론트: 선택된 `placeIds`가 있을 때만 추천 생성 JSON에 포함되는지, 없을 때 생략되는지 검증한다.
- 백엔드: `placeIds`가 있으면 상위 5곡만 `nearbyTracks`로 AI Client에 전달되는지 검증한다.
- 백엔드: `placeIds`가 없거나 집계 결과가 비어 있으면 `nearbyTracks`를 생략한 AI 요청이 만들어지는지 검증한다.
- 회귀: 날씨 실패 시 AI Client를 호출하지 않고 기존 `WEATHER_API_ERROR`가 유지되는지 검증한다.
