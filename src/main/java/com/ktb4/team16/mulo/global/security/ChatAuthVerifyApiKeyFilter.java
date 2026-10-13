package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.global.config.ChatAuthProperties;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ChatAuthVerifyApiKeyFilter extends OncePerRequestFilter {
    private static final String PATH = "/internal/auth/verify";
    private static final String HEADER = "X-Internal-Api-Key";

    private final ChatAuthProperties properties;
    private final SecurityErrorWriter errorWriter;

    public ChatAuthVerifyApiKeyFilter(ChatAuthProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.verifyApiKey();
        String supplied = request.getHeader(HEADER);
        if (expected == null || expected.isBlank() || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            errorWriter.write(response, ErrorCode.FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
    }
}
