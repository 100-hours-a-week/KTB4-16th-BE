package com.ktb4.team16.mulo.user.exception;

import com.ktb4.team16.mulo.global.error.FieldError;
import java.util.List;

public class DuplicateUserException extends RuntimeException {
    private final List<FieldError> fieldErrors;

    public DuplicateUserException(List<FieldError> fieldErrors) {
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
