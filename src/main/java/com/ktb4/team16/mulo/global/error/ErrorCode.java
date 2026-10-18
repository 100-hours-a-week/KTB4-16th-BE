package com.ktb4.team16.mulo.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰을 확인해주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효한 Refresh Token이 없습니다."),

    MATCH_VOTE_ANSWER_NOT_FOUND(HttpStatus.BAD_REQUEST, "투표 답변을 찾을 수 없습니다."),
    MATCH_VOTE_ANSWER_NOT_OWNED(HttpStatus.FORBIDDEN, "해당 투표 답변에 접근할 권한이 없습니다."),
    MATCH_VOTE_ANSWER_NOT_TODAY(HttpStatus.BAD_REQUEST, "오늘의 투표 답변이 아닙니다."),
    MATCH_REQUEST_CLOSED(HttpStatus.CONFLICT, "오늘 매칭 요청 가능 시간이 종료되었습니다."),
    TODAY_VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 취향 투표 질문을 찾을 수 없습니다."),
    VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "투표 질문을 찾을 수 없습니다."),
    VOTE_SNAPSHOT_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "오늘의 취향 투표를 준비 중입니다."),
    VOTE_QUESTION_NOT_ASSIGNED(HttpStatus.CONFLICT, "오늘 사용자에게 배정된 질문이 아닙니다."),
    VOTE_ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답변한 투표입니다."),
    VOTE_OPTIONS_IDENTICAL(HttpStatus.BAD_REQUEST, "두 선택지는 서로 달라야 합니다."),
    SELECTED_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "선택값을 입력해주세요."),

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    LATITUDE_REQUIRED(HttpStatus.BAD_REQUEST, "현재 위도가 필요합니다."),
    LONGITUDE_REQUIRED(HttpStatus.BAD_REQUEST, "현재 경도가 필요합니다."),
    PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "사진 파일이 필요합니다."),
    IMAGE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "사진 최대 용량은 10MB입니다."),
    IMAGE_PIXEL_COUNT_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE,
            "사진 해상도는 2,500만 픽셀 이하여야 합니다."),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 파일 형식입니다."),
    SW_LAT_REQUIRED(HttpStatus.BAD_REQUEST, "남서쪽 위도 값이 필요합니다."),
    SW_LNG_REQUIRED(HttpStatus.BAD_REQUEST, "남서쪽 경도 값이 필요합니다."),
    NE_LAT_REQUIRED(HttpStatus.BAD_REQUEST, "북동쪽 위도 값이 필요합니다."),
    NE_LNG_REQUIRED(HttpStatus.BAD_REQUEST, "북동쪽 경도 값이 필요합니다."),
    INVALID_LATITUDE(HttpStatus.BAD_REQUEST, "위도 값이 올바르지 않습니다."),
    INVALID_LONGITUDE(HttpStatus.BAD_REQUEST, "경도 값이 올바르지 않습니다."),
    INVALID_MAP_BOUNDS(HttpStatus.BAD_REQUEST, "지도 범위 정보가 올바르지 않습니다."),
    UNSUPPORTED_RECORD_LOCATION(HttpStatus.BAD_REQUEST, "현재 위치에서는 자물쇠를 생성할 수 없습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서입니다."),
    INVALID_SIZE(HttpStatus.BAD_REQUEST, "올바른 페이지 크기를 입력해주세요."),
    INVALID_RECORD_ID(HttpStatus.BAD_REQUEST, "올바른 자물쇠 ID를 입력해주세요."),
    MEMORY_SEARCH_NO_RECORDS(HttpStatus.BAD_REQUEST, "검색할 자물쇠가 없습니다."),
    COMMENT_TOO_LONG(HttpStatus.BAD_REQUEST, "코멘트는 80자 이하로 입력해주세요."),
    PLACE_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "장소 ID 목록이 필요합니다."),
    PLACE_IDS_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST,
            "한 번의 요청에는 장소 ID를 최대 100개까지 전달할 수 있습니다."),
    INVALID_PLACE_ID(HttpStatus.BAD_REQUEST, "장소 ID는 1 이상의 값이어야 합니다."),
    NOTIFICATION_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "읽음 처리할 알림 ID가 필요합니다."),
    INVALID_NOTIFICATION_ID(HttpStatus.BAD_REQUEST, "올바른 알림 ID를 입력해주세요."),
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
    SELF_FRIEND_REQUEST_NOT_ALLOWED(HttpStatus.CONFLICT,
            "자기 자신에게 친구 요청을 보낼 수 없습니다."),
    FRIEND_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 보낸 친구 요청이 있습니다."),
    FRIENDSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 친구 관계입니다."),
    MATCH_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 매칭 요청이 존재합니다."),
    MEMORY_SEARCH_IN_PROGRESS(HttpStatus.CONFLICT, "이전 기억 검색이 진행 중입니다."),
    MEMORY_SEARCH_CONVERSATION_GONE(HttpStatus.GONE, "대화가 종료되었거나 만료되었습니다."),

    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드를 찾을 수 없습니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "자물쇠를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없습니다."),
    FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다."),
    MATCH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "매칭 요청을 찾을 수 없습니다."),
    MONTHLY_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "월간 리포트를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    WEATHER_API_ERROR(HttpStatus.BAD_GATEWAY,
            "날씨 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    LOCATION_SERVICE_ERROR(HttpStatus.BAD_GATEWAY,
            "위치 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."),
    MUSIC_SEARCH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "음악 검색 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    MUSIC_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "음악 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요."),
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY,
            "AI 추천 서비스를 처리하지 못했습니다."),

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
