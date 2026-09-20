package com.ktb4.team16.mulo.auth.controller;

import com.ktb4.team16.mulo.auth.dto.request.LoginRequest;
import com.ktb4.team16.mulo.auth.dto.response.LoginResponse;
import com.ktb4.team16.mulo.auth.dto.response.LogoutResponse;
import com.ktb4.team16.mulo.auth.dto.response.RefreshResponse;
import com.ktb4.team16.mulo.auth.message.AuthMessage;
import com.ktb4.team16.mulo.auth.service.AuthService;
import com.ktb4.team16.mulo.auth.service.LoginResult;
import com.ktb4.team16.mulo.global.config.SecurityProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 604800;

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.email(), request.password());
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", result.refreshToken())
                // 재발급·로그아웃 API만 이 Cookie를 수신하게 범위를 제한한다.
                .path("/api/auth")
                .httpOnly(true)
                .secure(securityProperties.cookieSecure())
                .sameSite("Strict")
                .maxAge(REFRESH_TOKEN_MAX_AGE_SECONDS)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(AuthMessage.LOGIN_COMPLETED.message(), result.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        String accessToken = authService.refresh(refreshToken);
        return ResponseEntity.ok(new RefreshResponse(accessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        ResponseCookie expiredRefreshCookie = ResponseCookie.from("refreshToken", "")
                .path("/api/auth")
                .httpOnly(true)
                .secure(securityProperties.cookieSecure())
                .sameSite("Strict")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
                .body(new LogoutResponse(AuthMessage.LOGOUT_COMPLETED.message()));
    }
}
