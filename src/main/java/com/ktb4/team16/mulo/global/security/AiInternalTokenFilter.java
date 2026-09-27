package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnBean(AiProperties.class)
public class AiInternalTokenFilter extends OncePerRequestFilter {
    private final AiProperties properties;
    private final SecurityErrorWriter errorWriter;

    public AiInternalTokenFilter(AiProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    // AI 완료 콜백의 내부 토큰만 검증하고 일치하지 않으면 MVC 처리 전에 차단한다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Token");
        if (supplied == null || !MessageDigest.isEqual(
                properties.internalToken().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // 다른 API 경로에는 내부 AI 토큰 검증을 적용하지 않는다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/internal/ai/report-ready".equals(request.getRequestURI()));
    }
}
