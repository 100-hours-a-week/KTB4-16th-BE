package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.user.message.UserMessage;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findActiveUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UpdateNicknameResponse updateNickname(Long userId, String nickname) {
        User user = findActiveUser(userId);
        if (user.getNickname().equals(nickname)) {
            throw new NicknameConflictException(ErrorCode.SAME_NICKNAME);
        }
        if (userRepository.existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                nickname, userId)) {
            throw new NicknameConflictException(ErrorCode.NICKNAME_DUPLICATED);
        }
        try {
            user.updateNickname(nickname, currentKstDateTime());
            // 커밋 전 UNIQUE 위반을 확인해 API의 409 계약으로 변환한다.
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (collectCauseMessages(exception).contains("uk_users_active_nickname")) {
                throw new NicknameConflictException(ErrorCode.NICKNAME_DUPLICATED);
            }
            throw exception;
        }
        return UpdateNicknameResponse.from(user.getNickname());
    }

    @Transactional
    public UpdatePasswordResponse updatePassword(
            Long userId, String currentPassword, String newPassword) {
        User user = findActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new PasswordChangeException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new PasswordChangeException(ErrorCode.SAME_PASSWORD);
        }
        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.updatePassword(newPasswordHash, currentKstDateTime());
        return new UpdatePasswordResponse(UserMessage.PASSWORD_UPDATED.message());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
    }

    private LocalDateTime currentKstDateTime() {
        return LocalDateTime.now(SERVICE_ZONE_ID);
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
