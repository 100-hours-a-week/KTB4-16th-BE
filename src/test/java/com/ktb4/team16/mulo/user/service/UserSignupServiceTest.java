package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.FieldError;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSignupServiceTest {
    @Mock UserRepository userRepository;
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserSignupService service;

    @BeforeEach
    void setUp() {
        service = new UserSignupService(userRepository, passwordEncoder);
    }

    @Test
    void signupHashesPasswordBeforeSavingUser() {
        SignupCommand command = new SignupCommand("뮤로16", "user@example.com", "Test1234!");

        service.signup(command);

        var captor = ArgumentCaptor.forClass(com.ktb4.team16.mulo.user.entity.User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).hasSize(60)
                .isNotEqualTo(command.password());
        assertThat(passwordEncoder.matches(command.password(), captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void signupRejectsDuplicateEmailWithoutSaving() {
        SignupCommand command = new SignupCommand("뮤로16", "used@example.com", "Test1234!");
        when(userRepository.existsByEmailAndDeletedAtIsNull(command.email())).thenReturn(true);

        assertThatThrownBy(() -> service.signup(command))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void signupReportsEveryPrecheckedDuplicateField() {
        SignupCommand command = new SignupCommand("뮤로16", "used@example.com", "Test1234!");
        when(userRepository.existsByEmailAndDeletedAtIsNull(command.email())).thenReturn(true);
        when(userRepository.existsByNicknameAndDeletedAtIsNull(command.nickname())).thenReturn(true);

        DuplicateUserException exception = catchThrowableOfType(
                DuplicateUserException.class,
                () -> service.signup(command));

        assertThat(exception.fieldErrors()).containsExactly(
                FieldError.of("email", ErrorCode.EMAIL_DUPLICATED),
                FieldError.of("nickname", ErrorCode.NICKNAME_DUPLICATED));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void signupConvertsDatabaseUniqueRaceToDuplicateUserException() {
        SignupCommand command = new SignupCommand("뮤로16", "race@example.com", "Test1234!");
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_users_active_email"));

        DuplicateUserException exception = catchThrowableOfType(
                DuplicateUserException.class,
                () -> service.signup(command));

        assertThat(exception.fieldErrors())
                .containsExactly(FieldError.of("email", ErrorCode.EMAIL_DUPLICATED));
    }

    @Test
    void signupConvertsNicknameUniqueRaceToDuplicateUserException() {
        SignupCommand command = new SignupCommand("뮤로16", "race@example.com", "Test1234!");
        when(userRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_users_active_nickname"));

        DuplicateUserException exception = catchThrowableOfType(
                DuplicateUserException.class,
                () -> service.signup(command));

        assertThat(exception.fieldErrors())
                .containsExactly(FieldError.of("nickname", ErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    void signupDoesNotConvertUnrelatedDatabaseIntegrityFailureToDuplicate() {
        SignupCommand command = new SignupCommand("뮤로16", "user@example.com", "Test1234!");
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("unrelated_constraint");
        when(userRepository.saveAndFlush(any())).thenThrow(databaseException);

        assertThatThrownBy(() -> service.signup(command))
                .isSameAs(databaseException);
    }
}
