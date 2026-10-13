package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.UserWithdrawalException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {
    @Mock UserRepository userRepository;
    @Mock RecordRepository recordRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    UserWithdrawalService service;

    /** 각 테스트에 독립된 탈퇴 서비스를 구성한다. */
    @BeforeEach
    void setUp() {
        service = new UserWithdrawalService(userRepository, recordRepository,
                refreshTokenRepository, passwordEncoder);
    }

    /** 사용자·기록·토큰에 동일한 탈퇴 시각을 적용한다. */
    @Test
    void withdrawSoftDeletesActiveRecordsAndRevokesTokenAtSameTime() {
        User user = User.signup("withdraw@example.test", "hash", "user1");
        RefreshToken token = RefreshToken.create(user, "token-hash", LocalDateTime.now().plusDays(1));
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(refreshTokenRepository.findByUserUserId(7L)).thenReturn(Optional.of(token));

        service.withdraw(7L, "correct");

        ArgumentCaptor<LocalDateTime> at = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recordRepository).softDeleteActiveByUserId(eq(7L), at.capture());
        assertThat(user.getDeletedAt()).isEqualTo(at.getValue());
        assertThat(token.getRevokedAt()).isEqualTo(at.getValue());
    }

    /** 저장된 Refresh Token이 없어도 탈퇴가 성공한다. */
    @Test
    void withdrawSucceedsWithoutStoredRefreshToken() {
        User user = User.signup("withdraw@example.test", "hash", "user1");
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(refreshTokenRepository.findByUserUserId(7L)).thenReturn(Optional.empty());

        service.withdraw(7L, "correct");

        assertThat(user.getDeletedAt()).isNotNull();
        verify(recordRepository).softDeleteActiveByUserId(eq(7L), any());
    }

    /** 비밀번호가 틀리면 DB 변경을 시작하지 않는다. */
    @Test
    void wrongPasswordDoesNotChangeState() {
        User user = User.signup("withdraw@example.test", "hash", "user1");
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.withdraw(7L, "wrong"))
                .isInstanceOf(UserWithdrawalException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(user.getDeletedAt()).isNull();
        verify(recordRepository, never()).softDeleteActiveByUserId(any(), any());
    }

    /** 탈퇴한 계정으로 다시 요청하면 인증 실패로 처리한다. */
    @Test
    void alreadyWithdrawnUserIsUnauthorized() {
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(7L, "correct"))
                .isInstanceOf(UnauthenticatedUserException.class);
    }
}
