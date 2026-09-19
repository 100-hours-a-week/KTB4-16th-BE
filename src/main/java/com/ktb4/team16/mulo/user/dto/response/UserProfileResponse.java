package com.ktb4.team16.mulo.user.dto.response;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.message.UserMessage;

public record UserProfileResponse(String message, UserProfileData data) {
    public static UserProfileResponse from(User user) {
        // 중요: 토큰이 아니라 DB에서 조회한 최신 사용자 값으로 응답한다.
        return new UserProfileResponse(
                UserMessage.PROFILE_RETRIEVED.message(),
                new UserProfileData(user.getUserId(), user.getNickname(), user.getEmail()));
    }

    public record UserProfileData(Long userId, String nickname, String email) {
    }
}
