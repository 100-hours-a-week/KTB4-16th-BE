package com.ktb4.team16.mulo.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorWriter writer;

    public ApiAccessDeniedHandler(SecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            writer.write(response, 403, "CSRF_TOKEN_INVALID", "CSRF 토큰을 확인해주세요.");
            return;
        }
        writer.write(response, 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }
}
