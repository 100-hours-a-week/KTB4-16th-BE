package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePreferredGenresResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.event.UserNicknameChangedEvent;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.user.message.UserMessage;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new UserNicknameChangedEvent(userId));
        return UpdateNicknameResponse.from(user.getNickname());
    }

    /** 커밋 후 Chat Server에 전달할 최신 닉네임을 짧은 별도 조회 트랜잭션으로 읽는다. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public String getCurrentNicknameForChatSync(Long userId) {
        return findActiveUser(userId).getNickname();
    }

    @Transactional
    public UpdatePasswordResponse updatePassword(
            Long userId, String currentPassword, String newPassword) {
        // 탈퇴와 비밀번호 변경이 같은 해시를 동시에 검사하지 않도록 직렬화한다.
        User user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(UnauthenticatedUserException::new);
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

    @Transactional
    public UpdatePreferredGenresResponse updatePreferredGenres(
            Long userId, List<String> preferredGenres) {
        User user = findActiveUser(userId);
        user.updatePreferredGenres(preferredGenres);
        return UpdatePreferredGenresResponse.from(user);
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
