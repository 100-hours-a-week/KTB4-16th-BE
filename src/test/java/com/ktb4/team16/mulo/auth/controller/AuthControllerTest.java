package com.ktb4.team16.mulo.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.auth.service.AuthService;
import com.ktb4.team16.mulo.auth.service.LoginResult;
import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.global.config.SecurityProperties;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock
    private AuthService authService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService, new SecurityProperties(List.of(), false)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsAccessTokenBodyAndHttpOnlyRefreshCookie() throws Exception {
        when(authService.login("user@mulo.com", "plain-password"))
                .thenReturn(new LoginResult("access-token", "refresh-token"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@mulo.com\",\"password\":\"plain-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 완료"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refreshToken=refresh-token"),
                        org.hamcrest.Matchers.containsString("Max-Age=604800"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict")
                )));
    }

    @Test
    void loginRejectsBlankCredentialsWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void loginRejectsInvalidEmailFormat() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"plain-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_EMAIL_FORMAT"));
    }

    @Test
    void loginInvalidCredentialsReturnsUnifiedUnauthorizedError() throws Exception {
        when(authService.login("user@mulo.com", "wrong-password"))
                .thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@mulo.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unexpectedLoginFailureReturnsInternalServerError() throws Exception {
        when(authService.login("user@mulo.com", "plain-password"))
                .thenThrow(new IllegalStateException());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@mulo.com\",\"password\":\"plain-password\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void refreshReturnsOnlyNewAccessTokenAndKeepsRefreshCookie() throws Exception {
        when(authService.refresh("refresh-token")).thenReturn("new-access-token");

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refreshWithoutCookieReturnsUnifiedUnauthorizedError() throws Exception {
        when(authService.refresh(null)).thenThrow(new InvalidRefreshTokenException());

        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void unexpectedRefreshFailureReturnsInternalServerError() throws Exception {
        when(authService.refresh("refresh-token")).thenThrow(new IllegalStateException());

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refreshToken", "refresh-token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void logoutRevokesRefreshTokenAndExpiresCookie() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .cookie(new MockCookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refreshToken="),
                        org.hamcrest.Matchers.containsString("Max-Age=0"),
                        org.hamcrest.Matchers.containsString("Path=/api/auth"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict")
                )));

        verify(authService).logout("refresh-token");
    }

    @Test
    void logoutWithoutCookieStillSucceedsAndExpiresCookie() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authService).logout(null);
    }

    @Test
    void unexpectedLogoutFailureReturnsInternalServerError() throws Exception {
        doThrow(new IllegalStateException()).when(authService).logout("refresh-token");

        mvc.perform(post("/api/auth/logout")
                        .cookie(new MockCookie("refreshToken", "refresh-token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
