package com.ktb4.team16.mulo.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(ErrorCode code, String message, List<FieldError> errors) {
    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code, code.message(), List.of());
    }

    public static ErrorResponse of(ErrorCode code, List<FieldError> errors) {
        return new ErrorResponse(code, code.message(), errors);
    }
}
