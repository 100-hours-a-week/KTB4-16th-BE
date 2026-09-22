package com.ktb4.team16.mulo.music.client;

import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import java.util.List;

public interface MusicCatalogClient {
    // 정규화된 검색어로 외부 카탈로그의 트랙 목록을 조회한다.
    List<MusicSearchResult> searchTracks(String normalizedQuery);
}
