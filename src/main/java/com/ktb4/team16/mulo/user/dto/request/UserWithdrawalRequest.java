package com.ktb4.team16.mulo.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserWithdrawalRequest(
        @NotBlank(message = "PASSWORD_REQUIRED") String password
) { }
