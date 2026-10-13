package com.ktb4.team16.mulo.friend.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 닉네임으로 친구 요청할 사용자를 지정한다. */
public record SendFriendRequestRequest(
        @NotBlank(message = "NICKNAME_REQUIRED") String nickname
) {
}
