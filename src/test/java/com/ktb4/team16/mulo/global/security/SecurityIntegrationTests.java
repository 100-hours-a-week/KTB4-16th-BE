package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.auth.controller.AuthController;
import com.ktb4.team16.mulo.auth.service.AuthService;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(SecurityIntegrationTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "mulo.security.allowed-origins=http://localhost:3000",
        "mulo.security.cookie-secure=false",
        "mulo.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "mulo.jwt.access-token-ttl=PT1H",
        "mulo.jwt.refresh-token-ttl=P7D"
})
class SecurityIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void csrfBootstrapIssuesReadableStrictCookieWithoutSession() throws Exception {
        var result = mvc.perform(get("/api/csrf"))
                .andExpect(status().isNoContent()).andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void signupDoesNotRequireCsrfOrAuthentication() throws Exception {
        var result = mvc.perform(post("/api/users/signup"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginIsPublicButStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void refreshIsPublicButStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        Cookie cookie = csrfCookie();
        mvc.perform(post("/api/auth/refresh")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void logoutIsPublicButStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        Cookie cookie = csrfCookie();
        mvc.perform(post("/api/auth/logout")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void otherStateChangingApiStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/private"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void protectedGetIs401JsonWithoutRedirectOrSession() throws Exception {
        var result = mvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void weatherGetIsPublicWithoutAuthenticationOrCsrf() throws Exception {
        var result = mvc.perform(get("/api/weather"))
                .andExpect(status().isOk())
                .andExpect(content().string("weather"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void profileWithoutAccessTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void myPlacesWithoutAccessTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/users/me/places")
                        .param("swLat", "37.0")
                        .param("swLng", "127.0")
                        .param("neLat", "38.0")
                        .param("neLng", "128.0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void nicknameUpdateRequiresCsrfAndAuthentication() throws Exception {
        mvc.perform(patch("/api/users/me/nickname"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        Cookie cookie = csrfCookie();
        mvc.perform(patch("/api/users/me/nickname")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void passwordUpdateRequiresCsrfAndAuthentication() throws Exception {
        mvc.perform(patch("/api/users/me/password"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        Cookie cookie = csrfCookie();
        mvc.perform(patch("/api/users/me/password")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void csrfDoesNotReplaceAuthenticationAndSignupIsOnlyPublicForPost() throws Exception {
        Cookie cookie = csrfCookie();
        mvc.perform(post("/api/private").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/signup")).andExpect(status().isUnauthorized());
    }

    @Test
    void localPreflightAllowsOnlyConfiguredFrontend() throws Exception {
        mvc.perform(options("/api/users/signup").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/users/signup").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private Cookie csrfCookie() throws Exception {
        var result = mvc.perform(get("/api/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @ComponentScan("com.ktb4.team16.mulo.global")
    @Import({ProbeController.class, AuthController.class})
    static class TestConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }
    }

    // 실제 회원가입을 구현하지 않고 필터 통과 여부만 관찰하는 테스트 전용 엔드포인트.
    @RestController
    static class ProbeController {
        @PostMapping("/api/users/signup")
        @ResponseStatus(HttpStatus.CREATED)
        void signup() { }

        @GetMapping("/api/private")
        String privateResource() { return "protected"; }

        // 날씨 조회는 공용 예보 데이터만 반환하므로 비인증 GET을 허용한다.
        @GetMapping("/api/weather")
        String weather() { return "weather"; }
    }
}
