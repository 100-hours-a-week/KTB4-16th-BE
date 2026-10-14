package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.auth.repository.RefreshTokenRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.UserWithdrawalException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final RecordRepository recordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /** 사용자·기록 삭제와 Refresh Token 폐기를 하나의 DB 트랜잭션으로 처리한다. */
    @Transactional
    public void withdraw(Long userId, String password) {
        // 동일 사용자 로그인·비밀번호 변경·중복 탈퇴와 순서를 맞춘다.
        User user = userRepository.findByUserIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UserWithdrawalException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        LocalDateTime deletedAt = LocalDateTime.now(SERVICE_ZONE_ID);
        // 기존에 삭제된 기록과 사진 메타데이터, GCS 객체는 수정하지 않는다.
        recordRepository.softDeleteActiveByUserId(userId, deletedAt);
        refreshTokenRepository.findByUserUserId(userId)
                .ifPresent(token -> token.revoke(deletedAt));
        user.softDelete(deletedAt);
    }
}
