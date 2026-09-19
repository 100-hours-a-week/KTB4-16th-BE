package com.ktb4.team16.mulo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityErrorWriter securityErrorWriter;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessTokenForActiveUserSetsAuthentication() throws Exception {
        when(jwtTokenProvider.extractAccessUserId("access-token")).thenReturn(7L);
        when(userRepository.existsByUserIdAndDeletedAtIsNull(7L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer access-token");

        new JwtAuthenticationFilter(jwtTokenProvider, userRepository, securityErrorWriter)
                .doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deletedOrMissingUserDoesNotAuthenticate() throws Exception {
        when(jwtTokenProvider.extractAccessUserId("access-token")).thenReturn(7L);
        when(userRepository.existsByUserIdAndDeletedAtIsNull(7L)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.addHeader("Authorization", "Bearer access-token");

        new JwtAuthenticationFilter(jwtTokenProvider, userRepository, securityErrorWriter)
                .doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
