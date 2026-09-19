package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UpdateNicknameResponse updateNickname(Long userId, String nickname) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        if (user.getNickname().equals(nickname)) {
            throw new NicknameConflictException(ErrorCode.SAME_NICKNAME);
        }
        if (userRepository.existsByNicknameAndUserIdNotAndDeletedAtIsNull(
                nickname, userId)) {
            throw new NicknameConflictException(ErrorCode.NICKNAME_DUPLICATED);
        }
        try {
            user.updateNickname(nickname, LocalDateTime.now());
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
