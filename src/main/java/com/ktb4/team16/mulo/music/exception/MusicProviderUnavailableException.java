package com.ktb4.team16.mulo.music.exception;

public class MusicProviderUnavailableException extends RuntimeException {
    // 외부 공급자의 상세 오류를 안정적인 MULO 오류로 감싼다.
    public MusicProviderUnavailableException(Throwable cause) {
        super(cause);
    }
}
