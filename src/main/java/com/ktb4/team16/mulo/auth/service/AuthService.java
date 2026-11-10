package com.ktb4.team16.mulo.auth.service;

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
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String LEGACY_SESSION_ID_PREFIX = "legacy:";
    private static final Duration TOKEN_REVOCATION_SAFETY_MARGIN = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRevocationStore sessionRevocationStore;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            SessionRevocationStore sessionRevocationStore
    ) {
        this(userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider,
                sessionRevocationStore, jwtProperties.accessTokenTtl(), jwtProperties.refreshTokenTtl());
    }

    AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SessionRevocationStore sessionRevocationStore,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionRevocationStore = sessionRevocationStore;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /** 사용자 인증 후 같은 로그인 세션 ID가 든 Access·Refresh Token을 발급한다. */
    @Transactional
    public LoginResult login(String email, String password) {
        // 보조 인덱스는 읽기만 하고 기본 키로 잠가 탈퇴와 잠금 순서를 맞춘다.
        User candidate = userRepository.findByActiveEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        User user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(candidate.getUserId())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 이메일 존재 여부와 비밀번호 오류를 같은 401로 처리해 계정 열거를 막는다.
            throw new InvalidCredentialsException();
        }

        String sessionId = jwtTokenProvider.createSessionId();
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), sessionId);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), sessionId);
        saveRefreshToken(user, refreshToken);
        return new LoginResult(accessToken, refreshToken);
    }

    /** 유효한 Refresh Token의 세션이 아직 차단되지 않았을 때만 Access Token을 발급한다. */
    @Transactional(readOnly = true)
    public String refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        Long userId;
        String sessionId;
        try {
            userId = jwtTokenProvider.extractRefreshUserId(rawRefreshToken);
            sessionId = jwtTokenProvider.extractRefreshSessionId(rawRefreshToken)
                    .orElseGet(() -> LEGACY_SESSION_ID_PREFIX + userId);
        } catch (JwtException exception) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken storedToken = refreshTokenRepository.findByUserUserId(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        String presentedHash = hash(rawRefreshToken);
        boolean hashMatches = MessageDigest.isEqual(
                storedToken.getTokenHash().getBytes(StandardCharsets.UTF_8),
                presentedHash.getBytes(StandardCharsets.UTF_8)
        );
        boolean usable = hashMatches
                && storedToken.getRevokedAt() == null
                && storedToken.getExpiresAt().isAfter(LocalDateTime.now())
                && userRepository.existsByUserIdAndDeletedAtIsNull(userId);
        if (!usable) {
            throw new InvalidRefreshTokenException();
        }
        if (sessionRevocationStore.isSessionRevoked(sessionId)) {
            throw new InvalidRefreshTokenException();
        }

        // 기존 Refresh Token은 유지하고 Access Token에는 같은 로그인 세션 ID를 이어서 담는다.
        return jwtTokenProvider.createAccessToken(userId, sessionId);
    }

    /** Redis에 세션을 먼저 차단하고 기존 저장 Refresh Token도 revoke해 로그아웃을 완료한다. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        try {
            JwtTokenProvider.RefreshTokenDetails tokenDetails =
                    jwtTokenProvider.extractRefreshTokenDetailsForLogout(rawRefreshToken);
            Long userId = tokenDetails.userId();
            String sessionId = tokenDetails.sessionId() == null
                    ? LEGACY_SESSION_ID_PREFIX + userId
                    : tokenDetails.sessionId();
            sessionRevocationStore.revokeSession(
                    sessionId,
                    sessionRevocationTtl(),
                    userId,
                    legacyTokenRevocationTtl()
            );
        } catch (JwtException exception) {
            // 잘못된 Cookie도 기존 로그아웃 API처럼 멱등 처리하고 저장된 토큰 해시는 계속 확인한다.
        }

        String tokenHash = hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.revoke(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    /** 로그아웃된 세션과 남은 Access Token을 모두 덮는 Redis 표식 수명을 계산한다. */
    private Duration sessionRevocationTtl() {
        Duration accessTokenSafetyTtl = accessTokenTtl.plus(TOKEN_REVOCATION_SAFETY_MARGIN);
        return refreshTokenTtl.compareTo(accessTokenSafetyTtl) >= 0
                ? refreshTokenTtl
                : accessTokenSafetyTtl;
    }

    /** 세션 ID가 없던 Access Token의 최대 유효기간에 안전 여유를 더한다. */
    private Duration legacyTokenRevocationTtl() {
        return accessTokenTtl.plus(TOKEN_REVOCATION_SAFETY_MARGIN);
    }

    private void saveRefreshToken(User user, String rawRefreshToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plus(refreshTokenTtl);
        String tokenHash = hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .map(existing -> {
                    existing.replace(tokenHash, expiresAt);
                    return existing;
                })
                .orElseGet(() -> RefreshToken.create(user, tokenHash, expiresAt));
        // 원문이 아니라 일방향 해시만 DB에 저장한다.
        refreshTokenRepository.save(refreshToken);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
