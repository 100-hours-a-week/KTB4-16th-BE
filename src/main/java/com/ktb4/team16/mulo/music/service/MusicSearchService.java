package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.client.MusicCatalogClient;
import com.ktb4.team16.mulo.music.client.SpotifyApiException;
import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchInputException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MusicSearchService {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 255;

    private final MusicCatalogClient catalogClient;
    private final UserMusicSearchRateLimiter userRateLimiter;

    // 검색어와 호출량을 검증한 뒤 공급자 검색 결과를 반환한다.
    public List<MusicSearchResult> search(Long userId, String rawQuery) {
        String query = normalize(rawQuery);
        userRateLimiter.acquire(userId);
        try {
            // V1은 결과를 저장하거나 병합하지 않고 유효한 요청마다 공급자를 호출한다.
            return catalogClient.searchTracks(query);
        } catch (SpotifyApiException exception) {
            if (exception.kind() == SpotifyApiException.Kind.RATE_LIMITED) {
                throw new MusicSearchRateLimitedException(exception.retryAfter());
            }
            throw new MusicProviderUnavailableException(exception);
        }
    }

    // 검색어의 앞뒤·연속 공백을 정리하고 2~255자 정책을 검증한다.
    private String normalize(String rawQuery) {
        if (rawQuery == null) {
            throw new MusicSearchInputException();
        }
        String normalized = rawQuery.trim().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_QUERY_LENGTH || length > MAX_QUERY_LENGTH) {
            throw new MusicSearchInputException();
        }
        return normalized;
    }
}
