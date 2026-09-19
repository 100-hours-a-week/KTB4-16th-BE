package com.ktb4.team16.mulo.user.dto.response;

import com.ktb4.team16.mulo.user.message.UserMessage;

public record UpdateNicknameResponse(String message, UpdateNicknameData data) {
    public static UpdateNicknameResponse from(String nickname) {
        return new UpdateNicknameResponse(
                UserMessage.NICKNAME_UPDATED.message(),
                new UpdateNicknameData(nickname));
    }

    public record UpdateNicknameData(String nickname) {
    }
}
