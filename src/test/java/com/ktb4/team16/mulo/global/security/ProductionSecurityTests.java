package com.ktb4.team16.mulo.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
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
class ProductionSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

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
