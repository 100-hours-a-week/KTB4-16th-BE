package com.ktb4.team16.mulo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateNicknameRequest(
        @NotBlank(message = "NICKNAME_REQUIRED")
        @Pattern(regexp = "^(?:\\p{javaWhitespace}*|[A-Za-z0-9가-힣]{2,10})$",
                message = "INVALID_NICKNAME_FORMAT")
        String nickname
) { }
