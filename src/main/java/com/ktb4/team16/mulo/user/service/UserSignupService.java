package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.FieldError;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSignupService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupCommand command) {
        List<FieldError> errors = new ArrayList<>();
        if (userRepository.existsByEmailAndDeletedAtIsNull(command.email())) {
            errors.add(FieldError.of("email", ErrorCode.EMAIL_DUPLICATED));
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(command.nickname())) {
            errors.add(FieldError.of("nickname", ErrorCode.NICKNAME_DUPLICATED));
        }
        if (!errors.isEmpty()) {
            // 이메일·닉네임을 함께 알려 주기 위해 필드 오류 목록을 한 번에 전달한다.
            throw new DuplicateUserException(errors);
        }

        String passwordHash = passwordEncoder.encode(command.password());
        try {
            // 사전 중복 검사 이후 발생할 수 있는 동시 요청의 UNIQUE 경합을 여기서 확정한다.
            userRepository.saveAndFlush(
                    User.signup(command.email(), passwordHash, command.nickname()));
        } catch (DataIntegrityViolationException exception) {
            throw toDuplicateUserException(exception);
        }
    }

    private DuplicateUserException toDuplicateUserException(
            DataIntegrityViolationException exception) {
        String causeMessages = collectCauseMessages(exception);
        List<FieldError> errors = new ArrayList<>();

        if (causeMessages.contains("uk_users_active_email")) {
            errors.add(FieldError.of("email", ErrorCode.EMAIL_DUPLICATED));
        }
        if (causeMessages.contains("uk_users_active_nickname")) {
            errors.add(FieldError.of("nickname", ErrorCode.NICKNAME_DUPLICATED));
        }

        if (errors.isEmpty()) {
            throw exception;
        }
        return new DuplicateUserException(errors);
    }

    private String collectCauseMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}
