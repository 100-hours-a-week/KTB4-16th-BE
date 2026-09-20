package com.ktb4.team16.mulo.global.security;

import com.ktb4.team16.mulo.global.error.ErrorResponse;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorWriter {
    private final JsonMapper mapper = JsonMapper.builder().build();

    // 필터에서 발생한 오류는 MVC ControllerAdvice 밖이므로 여기서 JSON으로 응답한다.
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
