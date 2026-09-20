package com.ktb4.team16.mulo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                Duration.ofDays(7)
        );
    }

    @Test
    void loginStoresOnlyHashedRefreshTokenAndReturnsTokens() {
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        when(userRepository.findByEmailAndDeletedAtIsNull("user@mulo.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token-raw");

        LoginResult result = authService.login("user@mulo.com", "plain-password");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenCaptor.getValue();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token-raw");
        assertThat(savedRefreshToken.getTokenHash()).hasSize(64).isNotEqualTo("refresh-token-raw");
        assertThat(savedRefreshToken.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void loginReplacesExistingRefreshTokenForSameUser() {
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken existing = RefreshToken.create(
                user,
                "old-hash",
                LocalDateTime.of(2026, 9, 19, 12, 0)
        );
        when(userRepository.findByEmailAndDeletedAtIsNull("user@mulo.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("new-refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existing));

        authService.login("user@mulo.com", "plain-password");

        assertThat(existing.getTokenHash()).hasSize(64).isNotEqualTo("old-hash");
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void loginUsesSameExceptionForUnknownEmailAndWrongPassword() {
        when(userRepository.findByEmailAndDeletedAtIsNull("unknown@mulo.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown@mulo.com", "plain-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshIssuesOnlyNewAccessTokenForValidStoredRefreshToken() {
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
                LocalDateTime.now().plusDays(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));
        when(userRepository.existsByUserIdAndDeletedAtIsNull(42L)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(42L)).thenReturn("new-access-token");

        String accessToken = authService.refresh("refresh-token-raw");

        assertThat(accessToken).isEqualTo("new-access-token");
        verify(jwtTokenProvider, never()).createRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
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
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
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
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
                LocalDateTime.now().minusSeconds(1)
        );
        when(jwtTokenProvider.extractRefreshUserId("refresh-token-raw")).thenReturn(42L);
        when(refreshTokenRepository.findByUserUserId(42L)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh("refresh-token-raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsRevokedToken() {
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
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
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
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
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
                LocalDateTime.now().plusDays(1)
        );
        when(refreshTokenRepository.findByTokenHash(
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16"))
                .thenReturn(Optional.of(storedToken));

        authService.logout("refresh-token-raw");

        assertThat(storedToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    void logoutWithoutCookieIsIdempotent() {
        authService.logout(null);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logoutDoesNotRewriteAlreadyRevokedToken() {
        User user = User.signup("user@mulo.com", "encoded-password", "mulo");
        RefreshToken storedToken = RefreshToken.create(
                user,
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16",
                LocalDateTime.now().plusDays(1)
        );
        storedToken.revoke(LocalDateTime.now().minusHours(1));
        when(refreshTokenRepository.findByTokenHash(
                "5e4b06c757a1d6a5a10b96b0a1c1af9f9ef85e82c64967bfd724133436f58d16"))
                .thenReturn(Optional.of(storedToken));

        authService.logout("refresh-token-raw");

        verify(refreshTokenRepository, never()).save(any());
    }
}
