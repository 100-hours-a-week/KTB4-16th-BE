package com.ktb4.team16.mulo.auth.service;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.global.config.JwtProperties;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
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
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Duration refreshTokenTtl;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties
    ) {
        this(userRepository, refreshTokenRepository, passwordEncoder, jwtTokenProvider,
                jwtProperties.refreshTokenTtl());
    }

    AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            Duration refreshTokenTtl
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 이메일 존재 여부와 비밀번호 오류를 같은 401로 처리해 계정 열거를 막는다.
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        saveRefreshToken(user, refreshToken);
        return new LoginResult(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public String refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        Long userId;
        try {
            userId = jwtTokenProvider.extractRefreshUserId(rawRefreshToken);
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

        // Rotation 미적용 정책에 따라 기존 Refresh Token은 유지하고 Access Token만 발급한다.
        return jwtTokenProvider.createAccessToken(userId);
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
