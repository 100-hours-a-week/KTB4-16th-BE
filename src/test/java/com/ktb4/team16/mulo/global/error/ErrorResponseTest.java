package com.ktb4.team16.mulo.global.error;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void securityErrorOmitsEmptyFieldErrors() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.UNAUTHORIZED));

        assertThat(json).isEqualTo("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}");
    }

    @Test
    void validationErrorContainsFieldErrorDetails() {
        String json = mapper.writeValueAsString(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE,
                List.of(FieldError.of("email", ErrorCode.INVALID_EMAIL_FORMAT))));

        assertThat(json).contains("\"code\":\"INVALID_INPUT_VALUE\"")
                .contains("\"errors\":[{\"field\":\"email\"")
                .contains("\"code\":\"INVALID_EMAIL_FORMAT\"");
    }
}
