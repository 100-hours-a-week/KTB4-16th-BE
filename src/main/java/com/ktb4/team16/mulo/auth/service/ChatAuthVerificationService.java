package com.ktb4.team16.mulo.auth.service;

import com.ktb4.team16.mulo.auth.dto.response.ChatAuthVerifyResponse;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class ChatAuthVerificationService {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final JwtTokenProvider jwtTokenProvider;

    public ChatAuthVerificationService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ChatAuthVerifyResponse verify(Long authenticatedUserId, String accessToken) {
        if (authenticatedUserId == null) {
            throw new UnauthenticatedUserException();
        }

        JwtTokenProvider.AccessTokenDetails tokenDetails;
        try {
            tokenDetails = jwtTokenProvider.extractAccessTokenDetails(accessToken);
        } catch (JwtException exception) {
            throw new UnauthenticatedUserException();
        }
        if (!authenticatedUserId.equals(tokenDetails.userId())) {
            throw new UnauthenticatedUserException();
        }

        return new ChatAuthVerifyResponse(authenticatedUserId,
                OffsetDateTime.ofInstant(tokenDetails.expiresAt(), KOREA_ZONE));
    }
}
