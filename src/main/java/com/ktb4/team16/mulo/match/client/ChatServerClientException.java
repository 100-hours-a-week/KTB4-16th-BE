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
            case PARTICIPANT_CONFLICT -> "Chat Server의 매칭 참여자 정보가 충돌합니다.";
            case SERVER_ERROR -> "Chat Server 내부 오류가 발생했습니다.";
            case COMMUNICATION_ERROR -> "Chat Server와 통신하지 못했습니다.";
            case INVALID_RESPONSE -> "Chat Server 응답이 유효하지 않습니다.";
            case CONFIGURATION_ERROR -> "Chat Server 연동 설정이 올바르지 않습니다.";
        };
    }

    public enum FailureType {
        CLIENT_ERROR,
        PARTICIPANT_CONFLICT,
        SERVER_ERROR,
        COMMUNICATION_ERROR,
        INVALID_RESPONSE,
        CONFIGURATION_ERROR
    }
}
