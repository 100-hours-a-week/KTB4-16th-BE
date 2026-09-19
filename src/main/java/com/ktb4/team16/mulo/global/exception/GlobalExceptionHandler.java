package com.ktb4.team16.mulo.global.exception;

import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.ErrorResponse;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UnauthenticatedUserException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticatedUser(
            UnauthenticatedUserException exception) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status())
                .body(ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_REFRESH_TOKEN.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUser(DuplicateUserException exception) {
        return response(ErrorCode.DUPLICATE_RESOURCE, exception.fieldErrors());
    }

    @ExceptionHandler(NicknameConflictException.class)
    public ResponseEntity<ErrorResponse> handleNicknameConflict(
            NicknameConflictException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        List<com.ktb4.team16.mulo.global.error.FieldError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();
        return response(ErrorCode.INVALID_INPUT_VALUE, errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        // 내부 예외 정보는 노출하지 않고 모든 API에 같은 500 계약을 적용한다.
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private com.ktb4.team16.mulo.global.error.FieldError toFieldError(FieldError error) {
        ErrorCode code = ErrorCode.valueOf(error.getDefaultMessage());
        return com.ktb4.team16.mulo.global.error.FieldError.of(error.getField(), code);
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode code, List<com.ktb4.team16.mulo.global.error.FieldError> errors) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, errors));
    }
}
