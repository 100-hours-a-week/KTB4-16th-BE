package com.ktb4.team16.mulo.global.security.csrf;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        // 지연 토큰을 실제로 로드하여 응답의 XSRF-TOKEN Cookie 발급을 보장한다.
        // 프론트는 이 Cookie 원본을 X-XSRF-TOKEN 헤더로 보내야 한다.
        token.getToken();
    }
}
