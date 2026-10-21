package com.ktb4.team16.mulo.match.client;

public class ChatServerClientException extends RuntimeException {
    private final FailureType failureType;
    private final Integer httpStatus;

    public ChatServerClientException(FailureType failureType, Integer httpStatus) {
        super(messageFor(failureType));
        this.failureType = failureType;
        this.httpStatus = httpStatus;
    }

    public FailureType failureType() {
        return failureType;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    private static String messageFor(FailureType failureType) {
        return switch (failureType) {
            case CLIENT_ERROR -> "Chat Server가 요청을 거부했습니다.";
            case AUTHENTICATION_ERROR -> "Chat Server 내부 인증에 실패했습니다.";
            case REQUEST_CONFLICT -> "Chat Server의 기존 채팅방 요청 정보와 충돌합니다.";
            case GROUP_REENTRY_FORBIDDEN -> "이미 퇴장한 그룹 채팅방에는 다시 입장할 수 없습니다.";
            case EXPIRED_REQUEST -> "만료된 매칭에는 새 채팅방을 생성할 수 없습니다.";
            case SERVER_ERROR -> "Chat Server 내부 오류가 발생했습니다.";
            case COMMUNICATION_ERROR -> "Chat Server와 통신하지 못했습니다.";
            case INVALID_RESPONSE -> "Chat Server 응답이 유효하지 않습니다.";
            case CONFIGURATION_ERROR -> "Chat Server 연동 설정이 올바르지 않습니다.";
        };
    }

    public enum FailureType {
        CLIENT_ERROR,
        AUTHENTICATION_ERROR,
        REQUEST_CONFLICT,
        GROUP_REENTRY_FORBIDDEN,
        EXPIRED_REQUEST,
        SERVER_ERROR,
        COMMUNICATION_ERROR,
        INVALID_RESPONSE,
        CONFIGURATION_ERROR;

        public boolean isRetryable() {
            return this == SERVER_ERROR || this == COMMUNICATION_ERROR || this == INVALID_RESPONSE;
        }
    }
}
