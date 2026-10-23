package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.event.UserNicknameChangedEvent;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, passwordEncoder, eventPublisher);
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
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 12, 34, 56);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        UserProfileResponse response = service.getMyProfile(1L);

        assertThat(response.message()).isEqualTo("회원 정보 조회 성공");
        assertThat(response.data().userId()).isEqualTo(1L);
        assertThat(response.data().nickname()).isEqualTo("뮤로16");
        assertThat(response.data().email()).isEqualTo("user@example.com");
        assertThat(response.data().preferredGenres()).isNull();
        assertThat(response.data().genreOnboardingDone()).isFalse();
        assertThat(response.data().createdAt()).isEqualTo(createdAt);
    }

    @Test
    void getMyProfileTreatsOnlyValidPreferredGenresAsCompletedOnboarding() {
        User selected = User.signup("selected@example.com", "password-hash", "선택사용자");
        ReflectionTestUtils.setField(selected, "preferredGenres", List.of("재즈", "인디음악"));
        User inconsistentNull = User.signup("null@example.com", "password-hash", "기존사용자1");
        ReflectionTestUtils.setField(inconsistentNull, "genreOnboardingDone", true);
        User inconsistentEmpty = User.signup("empty@example.com", "password-hash", "기존사용자2");
        ReflectionTestUtils.setField(inconsistentEmpty, "preferredGenres", List.of());
        ReflectionTestUtils.setField(inconsistentEmpty, "genreOnboardingDone", true);
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(selected));
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(inconsistentNull));
        when(userRepository.findByUserIdAndDeletedAtIsNull(3L))
                .thenReturn(Optional.of(inconsistentEmpty));

        UserProfileResponse selectedResponse = service.getMyProfile(1L);
        UserProfileResponse nullResponse = service.getMyProfile(2L);
        UserProfileResponse emptyResponse = service.getMyProfile(3L);

        assertThat(selectedResponse.data().preferredGenres()).containsExactly("재즈", "인디음악");
        assertThat(selectedResponse.data().genreOnboardingDone()).isTrue();
        assertThat(nullResponse.data().preferredGenres()).isNull();
        assertThat(nullResponse.data().genreOnboardingDone()).isFalse();
        assertThat(emptyResponse.data().preferredGenres()).isEmpty();
        assertThat(emptyResponse.data().genreOnboardingDone()).isFalse();
        assertThat(inconsistentNull.isGenreOnboardingDone()).isTrue();
        assertThat(inconsistentEmpty.isGenreOnboardingDone()).isTrue();
    }

    @Test
    void updatePreferredGenresCompletesOnboardingWithoutChangingDailyMusicGenre() {
        User user = User.signup("user@example.com", "password-hash", "뮤로16");
        ReflectionTestUtils.setField(user, "musicGenre", "인디·재즈·R&B");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        var response = service.updatePreferredGenres(1L, List.of("재즈", "인디음악"));

        assertThat(user.getPreferredGenres()).containsExactly("재즈", "인디음악");
        assertThat(user.isGenreOnboardingDone()).isTrue();
        assertThat(user.getMusicGenre()).isEqualTo("인디·재즈·R&B");
        assertThat(response.data().preferredGenres()).containsExactly("재즈", "인디음악");
        assertThat(response.data().genreOnboardingDone()).isTrue();
    }

    @Test
    void emptyPreferredGenresCannotCompleteOnboarding() {
        User user = User.signup("user@example.com", "password-hash", "뮤로16");
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updatePreferredGenres(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getPreferredGenres()).isNull();
        assertThat(user.isGenreOnboardingDone()).isFalse();
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
        verify(eventPublisher).publishEvent(new UserNicknameChangedEvent(1L));
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
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
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
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
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
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatePasswordHashesNewPasswordAndUpdatesKstTimestamp() {
        String currentPassword = "Test1234!";
        String currentPasswordHash = passwordEncoder.encode(currentPassword);
        User user = User.signup("user@example.com", currentPasswordHash, "뮤로16");
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(1L))
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
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(1L))
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
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(1L))
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
