package com.ktb4.team16.mulo.auth.entity;

import com.ktb4.team16.mulo.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {
    @Test
    void replaceOverwritesHashAndClearsRevocation() {
        User user = User.signup("user@example.com", "password-hash", "뮤로16");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 25, 12, 0);
        LocalDateTime revokedAt = LocalDateTime.of(2026, 9, 18, 12, 0);
        LocalDateTime laterExpiresAt = LocalDateTime.of(2026, 10, 2, 12, 0);
        RefreshToken refreshToken = RefreshToken.create(user, "old-token-hash", expiresAt);

        refreshToken.revoke(revokedAt);
        refreshToken.replace("new-token-hash", laterExpiresAt);

        assertThat(refreshToken.getTokenHash()).isEqualTo("new-token-hash");
        assertThat(refreshToken.getExpiresAt()).isEqualTo(laterExpiresAt);
        assertThat(refreshToken.getRevokedAt()).isNull();
    }
}
