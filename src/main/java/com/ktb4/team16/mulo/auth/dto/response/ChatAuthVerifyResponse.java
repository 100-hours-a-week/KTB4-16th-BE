package com.ktb4.team16.mulo.auth.dto.response;

import java.time.OffsetDateTime;

public record ChatAuthVerifyResponse(Long userId, OffsetDateTime expiresAt) { }
