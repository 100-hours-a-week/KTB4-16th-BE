package com.ktb4.team16.mulo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePasswordRequest(
        @NotBlank(message = "CURRENT_PASSWORD_REQUIRED")
        String currentPassword,

        @NotBlank(message = "NEW_PASSWORD_REQUIRED")
        @Pattern(
                regexp = "^(?:\\p{javaWhitespace}*|(?=.*[a-z])(?=.*[A-Z])"
                        + "(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,16})$",
                message = "INVALID_PASSWORD_FORMAT")
        String newPassword
) { }
