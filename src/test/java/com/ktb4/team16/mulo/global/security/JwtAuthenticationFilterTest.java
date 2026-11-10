package com.ktb4.team16.mulo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.config.JwtProperties;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.global.security.session.SessionRevocationStore;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import io.lettuce.core.RedisCommandTimeoutException;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    private static final JwtProperties TEST_JWT_PROPERTIES = new JwtProperties(
            TestJwtSecret.value(), Duration.ofHours(1), Duration.ofDays(7));

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_JWT_PROPERTIES);
    private final SecurityErrorWriter securityErrorWriter = new SecurityErrorWriter();

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRevocationStore sessionRevocationStore;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 만료되지 않은 세션 토큰은 폐기 표식을 통과한 뒤 인증된다. */
    @Test
    void activeNewSessionSetsAuthentication() throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L, "active-session");
        when(sessionRevocationStore.isSessionRevoked("active-session")).thenReturn(false);
        when(userRepository.existsByUserIdAndDeletedAtIsNull(7L)).thenReturn(true);
        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        verify(sessionRevocationStore).isSessionRevoked("active-session");
        verify(filterChain).doFilter(request, response);
    }

    /** 로그아웃 표식이 있는 세션은 기존 401로 거부하고 필터 체인에 전달하지 않는다. */
    @Test
    void revokedSessionReturnsUnauthorizedWithoutAuthentication() throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L, "revoked-session");
        when(sessionRevocationStore.isSessionRevoked("revoked-session")).thenReturn(true);
        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.UNAUTHORIZED.status().value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    /** 세션 claim 없는 예전 Access Token은 사용자 레거시 표식으로 거부한다. */
    @Test
    void legacyTokenIsRejectedAfterLogoutMarker() throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L);
        when(sessionRevocationStore.areLegacyUserTokensRevoked(7L)).thenReturn(true);
        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.UNAUTHORIZED.status().value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    /** Redis 확인 실패는 기존 500으로 끝내고 인증을 설정하거나 전달하지 않는다. */
    @Test
    void redisFailureReturnsInternalServerErrorWithoutAuthentication() throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L, "session-redis-error");
        when(sessionRevocationStore.isSessionRevoked("session-redis-error"))
                .thenThrow(new RedisCommandTimeoutException("offline"));
        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.status().value());
        assertThat(response.getContentAsString()).contains(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    /** 탈퇴했거나 없는 사용자는 정상 세션 표식이 있어도 인증되지 않는다. */
    @Test
    void deletedOrMissingUserDoesNotAuthenticate() throws Exception {
        String token = jwtTokenProvider.createAccessToken(7L, "active-session");
        when(sessionRevocationStore.isSessionRevoked("active-session")).thenReturn(false);
        when(userRepository.existsByUserIdAndDeletedAtIsNull(7L)).thenReturn(false);
        MockHttpServletRequest request = requestWithBearer(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    /** 각 필터 회귀 테스트에 세션 폐기 저장소를 주입한다. */
    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                userRepository,
                sessionRevocationStore,
                securityErrorWriter
        );
    }

    /** 테스트용 bearer 요청을 구성한다. */
    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
