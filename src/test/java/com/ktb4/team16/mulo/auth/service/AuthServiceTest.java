package com.ktb4.team16.mulo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.global.config.JwtProperties;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.global.security.session.SessionRevocationStore;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import io.lettuce.core.RedisCommandTimeoutException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String TEST_EMAIL = "auth-user@example.test";
    private static final String UNKNOWN_EMAIL = "unknown@example.test";
    private static final String TEST_REFRESH_TOKEN = "refresh-token-raw";
    private static final String TEST_REFRESH_TOKEN_HASH = sha256(TEST_REFRESH_TOKEN);
    private static final JwtProperties TEST_JWT_PROPERTIES = new JwtProperties(
            Base64.getEncoder().encodeToString(new byte[32]), Duration.ofHours(1), Duration.ofDays(7));
    private static final JwtDecoder TEST_JWT_DECODER = NimbusJwtDecoder.withSecretKey(TEST_JWT_PROPERTIES.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SessionRevocationStore sessionRevocationStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                sessionRevocationStore,
                Duration.ofHours(1),
                Duration.ofDays(7)
        );
    }

    @Test
    void loginStoresOnlyHashedRefreshTokenAndReturnsTokens() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(userRepository.findByActiveEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(42L))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createSessionId()).thenReturn("test-session-id");
        when(jwtTokenProvider.createAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any(), anyString())).thenReturn("refresh-token-raw");

        LoginResult result = authService.login(TEST_EMAIL, "plain-password");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenCaptor.getValue();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token-raw");
        assertThat(savedRefreshToken.getTokenHash()).hasSize(64).isNotEqualTo("refresh-token-raw");
        assertThat(savedRefreshToken.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    /** 로그인 응답의 Access Token과 Refresh Token이 같은 세션 ID를 공유하는지 확인한다. */
    @Test
    void loginIssuesAccessAndRefreshTokensForSameSession() {
        LoginResult result = loginWithRealJwtProvider();

        String accessSessionId = decodeSessionId(result.accessToken());
        String refreshSessionId = decodeSessionId(result.refreshToken());
        assertThat(accessSessionId).isNotBlank().isEqualTo(refreshSessionId);
    }

    /** 새 로그인마다 기존 세션과 구별되는 ID를 발급하는지 확인한다. */
    @Test
    void newLoginCreatesDifferentSessionId() {
        prepareSuccessfulLogin();
        AuthService service = authServiceWithRealJwtProvider();
        LoginResult firstLogin = service.login(TEST_EMAIL, "plain-password");
        LoginResult secondLogin = service.login(TEST_EMAIL, "plain-password");

        String firstSessionId = decodeSessionId(firstLogin.refreshToken());
        String secondSessionId = decodeSessionId(secondLogin.refreshToken());
        assertThat(firstSessionId).isNotBlank().isNotEqualTo(secondSessionId);
    }

    @Test
    void loginReplacesExistingRefreshTokenForSameUser() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        RefreshToken existing = RefreshToken.create(
                user,
                "old-hash",
                LocalDateTime.of(2026, 9, 19, 12, 0)
        );
        when(userRepository.findByActiveEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(42L))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createSessionId()).thenReturn("test-session-id");
        when(jwtTokenProvider.createAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any(), anyString())).thenReturn("new-refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existing));

        authService.login(TEST_EMAIL, "plain-password");

        assertThat(existing.getTokenHash()).hasSize(64).isNotEqualTo("old-hash");
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void loginUsesSameExceptionForUnknownEmailAndWrongPassword() {
        when(userRepository.findByActiveEmail(UNKNOWN_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(UNKNOWN_EMAIL, "plain-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    /** 이메일 조회 뒤 탈퇴한 사용자는 잠금 재조회에서 거절한다. */
    @Test
    void loginRejectsUserWithdrawnAfterEmailLookup() {
        User candidate = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(candidate, "userId", 42L);
        when(userRepository.findByActiveEmail(TEST_EMAIL)).thenReturn(Optional.of(candidate));
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(42L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(TEST_EMAIL, "plain-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void refreshIssuesOnlyNewAccessTokenForValidStoredRefreshToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(jwtTokenProvider.extractRefreshSessionId("refresh-token-raw"))
                .thenReturn(Optional.of("test-session-id"));
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(eq(42L), anyString())).thenReturn("new-access-token");

        String accessToken = authService.refresh("refresh-token-raw");

        assertThat(accessToken).isEqualTo("new-access-token");
        verify(jwtTokenProvider, never()).createRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    /** 기존 Refresh Token의 세션 ID가 갱신된 Access Token에도 이어지는지 확인한다. */
    @Test
    void refreshPreservesRefreshTokenSessionId() {
        String sessionId = "existing-session-id";
        String rawRefreshToken = new JwtTokenProvider(TEST_JWT_PROPERTIES)
                .createRefreshToken(42L, sessionId);
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                sha256(rawRefreshToken),
                LocalDateTime.now().plusDays(1)
        );
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);

        String accessToken = authServiceWithRealJwtProvider().refresh(rawRefreshToken);

        assertThat(decodeSessionId(accessToken)).isEqualTo(sessionId);
    }

    /** 세션 claim 없는 과거 Refresh Token을 레거시 세션 ID로 갱신하는지 확인한다. */
    @Test
    void refreshUsesLegacySessionIdWhenClaimIsMissing() {
        String rawRefreshToken = new JwtTokenProvider(TEST_JWT_PROPERTIES).createRefreshToken(42L);
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                sha256(rawRefreshToken),
                LocalDateTime.now().plusDays(1)
        );
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);

        String accessToken = authServiceWithRealJwtProvider().refresh(rawRefreshToken);

        assertThat(decodeSessionId(accessToken)).isEqualTo("legacy:42");
    }

    /** Redis에 차단된 세션으로는 Access Token을 새로 발급하지 않는다. */
    @Test
    void refreshRejectsRevokedSession() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        when(jwtTokenProvider.extractRefreshUserId(TEST_REFRESH_TOKEN)).thenReturn(42L);
        when(jwtTokenProvider.extractRefreshSessionId(TEST_REFRESH_TOKEN))
                .thenReturn(Optional.of("revoked-session"));
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);
        when(sessionRevocationStore.isSessionRevoked("revoked-session")).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(TEST_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
    }

    /** Redis 확인이 실패하면 refresh 요청을 성공으로 처리하지 않는다. */
    @Test
    void refreshFailsClosedWhenRedisIsUnavailable() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        RedisCommandTimeoutException timeout = new RedisCommandTimeoutException("offline");
        when(jwtTokenProvider.extractRefreshUserId(TEST_REFRESH_TOKEN)).thenReturn(42L);
        when(jwtTokenProvider.extractRefreshSessionId(TEST_REFRESH_TOKEN))
                .thenReturn(Optional.of("session-42"));
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);
        when(sessionRevocationStore.isSessionRevoked("session-42")).thenThrow(timeout);

        assertThatThrownBy(() -> authService.refresh(TEST_REFRESH_TOKEN)).isSameAs(timeout);
    }

    @Test
    void refreshRejectsTokenWhenJwtValidationFails() {
        when(jwtTokenProvider.extractRefreshUserId("invalid-token"))
                .thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsTokenWhenStoredHashDoesNotMatch() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                LocalDateTime.now().plusDays(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh("refresh-token-raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().minusSeconds(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh("refresh-token-raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsRevokedToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        storedToken.revoke(LocalDateTime.now());
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh("refresh-token-raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsInactiveUserToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("refresh-token-raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesStoredRefreshTokenUsingCookieHash() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        stubRefreshClaims(TEST_REFRESH_TOKEN, 42L, Optional.of("session-42"));
        when(refreshTokenRepository.findByTokenHash(TEST_REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(storedToken));

        authService.logout("refresh-token-raw");

        assertThat(storedToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(storedToken);
    }

    /** 로그아웃은 Redis 차단 표식을 저장한 뒤 기존 Refresh Token 행을 revoke한다. */
    @Test
    void logoutRevokesRedisSessionAndStoredRefreshToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        stubRefreshClaims(TEST_REFRESH_TOKEN, 42L, Optional.of("session-42"));
        when(refreshTokenRepository.findByTokenHash(TEST_REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(storedToken));

        authService.logout(TEST_REFRESH_TOKEN);

        InOrder order = inOrder(sessionRevocationStore, refreshTokenRepository);
        order.verify(sessionRevocationStore).revokeSession(
                "session-42", Duration.ofDays(7), 42L, Duration.ofHours(1).plusMinutes(1));
        order.verify(refreshTokenRepository).save(storedToken);
    }

    /** 세션 claim 없는 과거 Refresh Token은 사용자 단위 레거시 ID로 차단한다. */
    @Test
    void logoutUsesLegacySessionIdForOldRefreshToken() {
        stubRefreshClaims(TEST_REFRESH_TOKEN, 42L, Optional.empty());
        when(refreshTokenRepository.findByTokenHash(TEST_REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());

        authService.logout(TEST_REFRESH_TOKEN);

        verify(sessionRevocationStore).revokeSession(
                "legacy:42", Duration.ofDays(7), 42L, Duration.ofHours(1).plusMinutes(1));
    }

    /** 만료됐지만 서명이 유효한 Refresh Token도 로그아웃 시 해당 Access Token 세션을 차단한다. */
    @Test
    void logoutRevokesSessionWhenRefreshTokenHasExpired() {
        String expiredToken = expiredRefreshToken(42L, "expired-session");

        authServiceWithRealJwtProvider().logout(expiredToken);

        verify(sessionRevocationStore).revokeSession(
                "expired-session", Duration.ofDays(7), 42L, Duration.ofHours(1).plusMinutes(1));
    }

    @Test
    void logoutWithoutCookieIsIdempotent() {
        authService.logout(null);

        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(sessionRevocationStore);
    }

    @Test
    void logoutDoesNotRewriteAlreadyRevokedToken() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        RefreshToken storedToken = RefreshToken.create(
                user,
                TEST_REFRESH_TOKEN_HASH,
                LocalDateTime.now().plusDays(1)
        );
        storedToken.revoke(LocalDateTime.now().minusHours(1));
        stubRefreshClaims(TEST_REFRESH_TOKEN, 42L, Optional.of("session-42"));
        when(refreshTokenRepository.findByTokenHash(TEST_REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(storedToken));

        authService.logout("refresh-token-raw");

        verify(refreshTokenRepository, never()).save(any());
    }

    /** 실제 JWT 서명 키를 사용하는 로그인으로 발급된 토큰의 세션 claim을 검증한다. */
    private LoginResult loginWithRealJwtProvider() {
        prepareSuccessfulLogin();

        return authServiceWithRealJwtProvider().login(TEST_EMAIL, "plain-password");
    }

    /** 실제 JWT provider를 사용하는 서비스 인스턴스를 구성한다. */
    private AuthService authServiceWithRealJwtProvider() {
        return new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                new JwtTokenProvider(TEST_JWT_PROPERTIES),
                sessionRevocationStore,
                TEST_JWT_PROPERTIES.accessTokenTtl(),
                TEST_JWT_PROPERTIES.refreshTokenTtl()
        );
    }

    /** 로그아웃 테스트가 서명된 Refresh Token에서 읽을 사용자·세션 정보를 준비한다. */
    private void stubRefreshClaims(String token, Long userId, Optional<String> sessionId) {
        when(jwtTokenProvider.extractRefreshTokenDetailsForLogout(token))
                .thenReturn(new JwtTokenProvider.RefreshTokenDetails(userId, sessionId.orElse(null)));
    }

    /** 로그아웃 검증을 위해 테스트 키로 서명된 만료 Refresh Token을 만든다. */
    private static String expiredRefreshToken(Long userId, String sessionId) {
        Instant expiredAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(expiredAt.minus(Duration.ofDays(1)))
                .expiresAt(expiredAt)
                .claim("tokenType", "refresh")
                .claim("sessionId", sessionId)
                .build();
        var encoder = NimbusJwtEncoder.withSecretKey(TEST_JWT_PROPERTIES.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    /** 성공 로그인에 필요한 사용자 조회와 비밀번호 검증 결과를 준비한다. */
    private void prepareSuccessfulLogin() {
        User user = User.signup(TEST_EMAIL, "encoded-password", "test-user");
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(userRepository.findByActiveEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
    }

    /** 테스트 서명 검증기를 사용해 JWT의 세션 ID를 읽는다. */
    private static String decodeSessionId(String token) {
        return TEST_JWT_DECODER.decode(token).getClaimAsString("sessionId");
    }

    /** 테스트 입력과 동일한 방식으로 DB 저장용 SHA-256 해시를 계산한다. */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
