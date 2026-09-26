package com.ktb4.team16.mulo.global.exception;

import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.ErrorResponse;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchInputException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import com.ktb4.team16.mulo.place.exception.InvalidMapBoundsException;
import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.exception.MissingCommentFieldException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import com.ktb4.team16.mulo.upload.exception.EmptyImageException;
import com.ktb4.team16.mulo.upload.exception.ImageSizeExceededException;
import com.ktb4.team16.mulo.upload.exception.InvalidImageFormatException;
import com.ktb4.team16.mulo.upload.exception.UploadNotFoundException;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.exception.WeatherRequestTimeException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(PasswordChangeException.class)
    public ResponseEntity<ErrorResponse> handlePasswordChange(
            PasswordChangeException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(WeatherApiException.class)
    public ResponseEntity<ErrorResponse> handleWeatherApi(WeatherApiException exception) {
        return ResponseEntity.status(ErrorCode.WEATHER_API_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.WEATHER_API_ERROR));
    }

    @ExceptionHandler(WeatherRequestTimeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWeatherRequestTime(
            WeatherRequestTimeException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_WEATHER_REQUEST_TIME.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_WEATHER_REQUEST_TIME));
    }

    @ExceptionHandler(InvalidMapBoundsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMapBounds(
            InvalidMapBoundsException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_MAP_BOUNDS.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_MAP_BOUNDS));
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCursor(
            InvalidCursorException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_CURSOR.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_CURSOR));
    }

    @ExceptionHandler(InvalidRecordIdException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRecordId(
            InvalidRecordIdException exception) {
        return response(ErrorCode.INVALID_INPUT_VALUE,
                List.of(com.ktb4.team16.mulo.global.error.FieldError.of(
                        "recordId", ErrorCode.INVALID_RECORD_ID)));
    }

    @ExceptionHandler(MissingCommentFieldException.class)
    public ResponseEntity<ErrorResponse> handleMissingCommentField(
            MissingCommentFieldException exception) {
        return response(ErrorCode.INVALID_INPUT_VALUE,
                List.of(com.ktb4.team16.mulo.global.error.FieldError.of(
                        "comment", ErrorCode.INVALID_INPUT_VALUE)));
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecordNotFound(
            RecordNotFoundException exception) {
        return ResponseEntity.status(ErrorCode.RECORD_NOT_FOUND.status())
                .body(ErrorResponse.of(ErrorCode.RECORD_NOT_FOUND));
    }

    @ExceptionHandler(MusicSearchRateLimitedException.class)
    // 음악 검색 제한을 429와 Retry-After 헤더로 변환한다.
    public ResponseEntity<ErrorResponse> handleMusicSearchRateLimited(
            MusicSearchRateLimitedException exception) {
        long retryAfterSeconds = Math.max(1,
                (exception.retryAfter().toMillis() + 999) / 1_000);
        // 중요: Spotify 오류 본문은 숨기고 클라이언트에 필요한 대기 시간만 전달한다.
        return ResponseEntity.status(ErrorCode.MUSIC_SEARCH_RATE_LIMITED.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(ErrorResponse.of(ErrorCode.MUSIC_SEARCH_RATE_LIMITED));
    }

    @ExceptionHandler(MusicProviderUnavailableException.class)
    // 공급자 상세 원인을 노출하지 않고 안정적인 503 응답으로 변환한다.
    public ResponseEntity<ErrorResponse> handleMusicProviderUnavailable(
            MusicProviderUnavailableException exception) {
        return ResponseEntity.status(ErrorCode.MUSIC_PROVIDER_UNAVAILABLE.status())
                .body(ErrorResponse.of(ErrorCode.MUSIC_PROVIDER_UNAVAILABLE));
    }

    @ExceptionHandler(MusicSearchInputException.class)
    // 서비스 계층의 검색어 검증 실패를 공통 400 응답으로 변환한다.
    public ResponseEntity<ErrorResponse> handleMusicSearchInput(
            MusicSearchInputException exception) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE));
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

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleInvalidParameter(Exception exception) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception) {
        return response(ErrorCode.INVALID_INPUT_VALUE,
                List.of(com.ktb4.team16.mulo.global.error.FieldError.of(
                        exception.getRequestPartName(), ErrorCode.PHOTO_REQUIRED)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.IMAGE_SIZE_EXCEEDED.status())
                .body(ErrorResponse.of(ErrorCode.IMAGE_SIZE_EXCEEDED));
    }

    @ExceptionHandler(EmptyImageException.class)
    public ResponseEntity<ErrorResponse> handleEmptyImage(EmptyImageException exception) {
        return response(ErrorCode.INVALID_INPUT_VALUE,
                List.of(com.ktb4.team16.mulo.global.error.FieldError.of(
                        "photo", ErrorCode.PHOTO_REQUIRED)));
    }

    @ExceptionHandler(ImageSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageSizeExceeded(
            ImageSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.IMAGE_SIZE_EXCEEDED.status())
                .body(ErrorResponse.of(ErrorCode.IMAGE_SIZE_EXCEEDED));
    }

    @ExceptionHandler(InvalidImageFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidImageFormat(
            InvalidImageFormatException exception) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_IMAGE_FORMAT.status())
                .body(ErrorResponse.of(ErrorCode.UNSUPPORTED_IMAGE_FORMAT));
    }

    @ExceptionHandler(UploadNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUploadNotFound(UploadNotFoundException exception) {
        return ResponseEntity.status(ErrorCode.UPLOAD_NOT_FOUND.status())
                .body(ErrorResponse.of(ErrorCode.UPLOAD_NOT_FOUND));
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
