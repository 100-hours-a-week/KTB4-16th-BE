package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.report.config.ReportOperationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/** 리포트 수동 실행 내부 요청의 전용 운영 토큰을 검증한다. */
public class ReportOperationTokenFilter extends OncePerRequestFilter {
    private static final String PATH = "/internal/ops/monthly-reports/generate";
    private final ReportOperationProperties properties;
    private final SecurityErrorWriter errorWriter;

    public ReportOperationTokenFilter(ReportOperationProperties properties,
            SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    // 요청 토큰을 상수 시간 비교하고 불일치한 내부 실행 요청을 차단한다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Report-Operation-Token");
        if (supplied == null || !MessageDigest.isEqual(
                properties.token().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // 지정된 운영 POST 외에는 이 필터를 적용하지 않는다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
    }
}
