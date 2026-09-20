package com.ktb4.team16.mulo.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "EMAIL_REQUIRED")
        @Email(message = "INVALID_EMAIL_FORMAT")
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED") String password
) { }
