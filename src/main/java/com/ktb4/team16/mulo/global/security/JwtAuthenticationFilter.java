package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.global.security.session.SessionRevocationStore;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final SessionRevocationStore sessionRevocationStore;
    private final SecurityErrorWriter securityErrorWriter;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            SessionRevocationStore sessionRevocationStore,
            SecurityErrorWriter securityErrorWriter
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.sessionRevocationStore = sessionRevocationStore;
        this.securityErrorWriter = securityErrorWriter;
    }

    /** JWT의 서명·세션 폐기·사용자 상태를 확인한 뒤 요청 인증을 설정한다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            JwtTokenProvider.AccessTokenDetails tokenDetails;
            try {
                tokenDetails = jwtTokenProvider.extractAccessTokenDetails(
                        authorization.substring(BEARER_PREFIX.length()));
            } catch (JwtException exception) {
                securityErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
                return;
            }

            boolean revoked;
            try {
                revoked = tokenDetails.sessionId() == null
                        ? sessionRevocationStore.areLegacyUserTokensRevoked(tokenDetails.userId())
                        : sessionRevocationStore.isSessionRevoked(tokenDetails.sessionId());
            } catch (RuntimeException exception) {
                securityErrorWriter.write(response, ErrorCode.INTERNAL_SERVER_ERROR);
                return;
            }
            if (revoked) {
                securityErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
                return;
            }

            if (userRepository.existsByUserIdAndDeletedAtIsNull(tokenDetails.userId())) {
                // 서명이 유효해도 탈퇴 사용자는 즉시 접근을 잃는다.
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        tokenDetails.userId(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/csrf")
                || path.startsWith("/api/auth/")
                || (path.equals("/api/users/signup") && "POST".equals(request.getMethod()));
    }
}
