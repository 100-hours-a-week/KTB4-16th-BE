package com.ktb4.team16.mulo.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserSignupRequest(
        @NotBlank(message = "NICKNAME_REQUIRED")
        @Pattern(regexp = "^[A-Za-z0-9가-힣]{2,10}$", message = "INVALID_NICKNAME_FORMAT")
        String nickname,

        @NotBlank(message = "EMAIL_REQUIRED")
        @Email(message = "INVALID_EMAIL_FORMAT")
        String email,

        @NotBlank(message = "PASSWORD_REQUIRED")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16}$",
                message = "INVALID_PASSWORD_FORMAT")
        String password
) { }
