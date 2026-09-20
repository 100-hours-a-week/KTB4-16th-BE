package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock UserRepository userRepository;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, passwordEncoder);
    }

    @Test
    void getMyProfileRejectsMissingActiveUser() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(1L))
                .isInstanceOf(UnauthenticatedUserException.class);
    }

    @Test
    void getMyProfileReturnsCurrentUserData() {
        User user = User.signup("user@example.com", "password-hash", "뮤로16");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        UserProfileResponse response = service.getMyProfile(1L);

        assertThat(response.message()).isEqualTo("회원 정보 조회 성공");
        assertThat(response.data().userId()).isEqualTo(1L);
        assertThat(response.data().nickname()).isEqualTo("뮤로16");
        assertThat(response.data().email()).isEqualTo("user@example.com");
    }

    @Test
    void updateNicknameChangesNicknameAndUpdatedAt() {
        User user = User.signup("user@example.com", "password-hash", "기존닉네임");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        LocalDateTime beforeUpdate = LocalDateTime.now();

        UpdateNicknameResponse response = service.updateNickname(1L, "새닉네임");

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
        assertThat(response.message()).isEqualTo("닉네임이 변경되었습니다.");
        assertThat(response.data().nickname()).isEqualTo("새닉네임");
    }

    @Test
    void updateNicknameRejectsCurrentNickname() {
        User user = User.signup("user@example.com", "password-hash", "현재닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateNickname(1L, "현재닉네임"))
                .isInstanceOfSatisfying(NicknameConflictException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.SAME_NICKNAME));
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void updateNicknameRejectsNicknameUsedByAnotherActiveUser() {
        User user = User.signup("user@example.com", "password-hash", "현재닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                "사용중닉네임", 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateNickname(1L, "사용중닉네임"))
                .isInstanceOfSatisfying(NicknameConflictException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
        assertThat(user.getNickname()).isEqualTo("현재닉네임");
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void updateNicknameDoesNotTreatCurrentUserAsDuplicateWhenOnlyCaseChanges() {
        User user = User.signup("user@example.com", "password-hash", "Mulo");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        // MySQL utf8mb4_0900_ai_ci에서는 현재 행도 대소문자 구분 없이 일치한다.
        lenient().when(userRepository.existsByNicknameAndDeletedAtIsNull("mulo"))
                .thenReturn(true);

        UpdateNicknameResponse response = service.updateNickname(1L, "mulo");

        assertThat(response.data().nickname()).isEqualTo("mulo");
        assertThat(user.getNickname()).isEqualTo("mulo");
    }

    @Test
    void updateNicknameConvertsDatabaseUniqueRaceToDuplicateConflict() {
        User user = User.signup("user@example.com", "password-hash", "현재닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        doThrow(new DataIntegrityViolationException("uk_users_active_nickname"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> service.updateNickname(1L, "경합닉네임"))
                .isInstanceOfSatisfying(NicknameConflictException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    void updateNicknameDoesNotHideUnrelatedDatabaseFailure() {
        User user = User.signup("user@example.com", "password-hash", "현재닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("unrelated_constraint");
        doThrow(databaseException).when(userRepository).flush();

        assertThatThrownBy(() -> service.updateNickname(1L, "새닉네임"))
                .isSameAs(databaseException);
    }

    @Test
    void updatePasswordHashesNewPasswordAndUpdatesKstTimestamp() {
        String currentPassword = "Test1234!";
        String currentPasswordHash = passwordEncoder.encode(currentPassword);
        User user = User.signup("user@example.com", currentPasswordHash, "뮤로16");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));
        LocalDateTime beforeUpdate = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        UpdatePasswordResponse response = service.updatePassword(
                1L, currentPassword, "New1234!");

        assertThat(user.getPasswordHash()).isNotEqualTo("New1234!");
        assertThat(passwordEncoder.matches("New1234!", user.getPasswordHash())).isTrue();
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
        assertThat(response.message()).isEqualTo("비밀번호가 변경되었습니다.");
    }

    @Test
    void updatePasswordRejectsMismatchedCurrentPassword() {
        String currentPasswordHash = passwordEncoder.encode("Test1234!");
        User user = User.signup("user@example.com", currentPasswordHash, "뮤로16");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updatePassword(
                1L, "Wrong1234!", "New1234!"))
                .isInstanceOfSatisfying(PasswordChangeException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH));
        assertThat(user.getPasswordHash()).isEqualTo(currentPasswordHash);
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void updatePasswordRejectsSameNewPassword() {
        String currentPassword = "Test1234!";
        String currentPasswordHash = passwordEncoder.encode(currentPassword);
        User user = User.signup("user@example.com", currentPasswordHash, "뮤로16");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updatePassword(
                1L, currentPassword, currentPassword))
                .isInstanceOfSatisfying(PasswordChangeException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.SAME_PASSWORD));
        assertThat(user.getPasswordHash()).isEqualTo(currentPasswordHash);
        assertThat(user.getUpdatedAt()).isNull();
    }
}
