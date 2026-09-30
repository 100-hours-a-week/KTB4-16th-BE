package com.ktb4.team16.mulo.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(SecurityIntegrationTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "mulo.security.allowed-origins=https://mulostudio.com",
        "mulo.jwt.access-token-ttl=PT1H",
        "mulo.jwt.refresh-token-ttl=P7D"
})
class ProductionSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    /** 테스트 컨텍스트에 하드코딩하지 않은 JWT 서명 키를 제공한다. */
    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("mulo.jwt.secret", TestJwtSecret::value);
    }

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void productionCookieIsSecureStrictAndHostOnly() throws Exception {
        var result = mvc.perform(get("/api/csrf").secure(true))
                .andExpect(status().isNoContent()).andReturn();
        var cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    void productionDoesNotAllowLocalCrossOriginRequests() throws Exception {
        mvc.perform(options("/api/users/signup").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // 운영 프론트 도메인의 사전 요청에는 인증 Cookie 사용을 위한 CORS 헤더를 반환한다.
    @Test
    void productionAllowsMulostudioCrossOriginRequests() throws Exception {
        mvc.perform(options("/api/users/signup").header("Origin", "https://mulostudio.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://mulostudio.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void sameOriginBootstrapIsAllowed() throws Exception {
        mvc.perform(get("/api/csrf").header("Origin", "http://localhost"))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedRequestCanReachProtectedApiWithoutSession() throws Exception {
        var result = mvc.perform(get("/api/private").with(user("test-user")))
                .andExpect(status().isOk()).andExpect(content().string("protected")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void nonApiRequestIsDeniedEvenForAuthenticatedUser() throws Exception {
        mvc.perform(get("/internal").with(user("test-user")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void existingSessionCannotAuthenticateRequest() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("test-user", "unused", java.util.List.of());
        session.setAttribute("SPRING_SECURITY_CONTEXT",
                new org.springframework.security.core.context.SecurityContextImpl(authentication));
        mvc.perform(get("/api/private").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void bcryptOutputFitsExistingColumnAndCanAuthenticatePassword() {
        String password = "Test1234!";
        String hash = encoder.encode(password);
        assertThat(hash).hasSize(60).isNotEqualTo(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
