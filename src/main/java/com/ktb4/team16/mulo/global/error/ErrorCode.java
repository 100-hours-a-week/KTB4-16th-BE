package com.ktb4.team16.mulo.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰을 확인해주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효한 Refresh Token이 없습니다."),

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    SW_LAT_REQUIRED(HttpStatus.BAD_REQUEST, "남서쪽 위도 값이 필요합니다."),
    SW_LNG_REQUIRED(HttpStatus.BAD_REQUEST, "남서쪽 경도 값이 필요합니다."),
    NE_LAT_REQUIRED(HttpStatus.BAD_REQUEST, "북동쪽 위도 값이 필요합니다."),
    NE_LNG_REQUIRED(HttpStatus.BAD_REQUEST, "북동쪽 경도 값이 필요합니다."),
    INVALID_LATITUDE(HttpStatus.BAD_REQUEST, "위도 값이 올바르지 않습니다."),
    INVALID_LONGITUDE(HttpStatus.BAD_REQUEST, "경도 값이 올바르지 않습니다."),
    INVALID_MAP_BOUNDS(HttpStatus.BAD_REQUEST, "지도 범위 정보가 올바르지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서입니다."),
    PLACE_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "장소 ID 목록이 필요합니다."),
    INVALID_PLACE_ID(HttpStatus.BAD_REQUEST, "장소 ID는 1 이상의 값이어야 합니다."),
    INVALID_WEATHER_REQUEST_TIME(HttpStatus.BAD_REQUEST, "지난 시간대의 날씨는 조회할 수 없습니다."),
    NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "닉네임을 입력해주세요."),
    INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "닉네임은 띄어쓰기와 특수문자 없이 2~10자로 입력해주세요."),
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "올바른 이메일 주소를 입력해주세요."),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호를 입력해주세요."),
    CURRENT_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "현재 비밀번호를 입력해주세요."),
    NEW_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "새 비밀번호를 입력해주세요."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST,
            "비밀번호는 영문 대소문자, 숫자, 특수문자를 포함해 8~16자여야 합니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 사용 중인 정보가 있습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    SAME_NICKNAME(HttpStatus.CONFLICT, "현재 닉네임과 동일합니다."),
    SAME_PASSWORD(HttpStatus.CONFLICT, "새 비밀번호는 현재 비밀번호와 달라야 합니다."),

    WEATHER_API_ERROR(HttpStatus.BAD_GATEWAY,
            "날씨 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    MUSIC_SEARCH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "음악 검색 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    MUSIC_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "음악 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
