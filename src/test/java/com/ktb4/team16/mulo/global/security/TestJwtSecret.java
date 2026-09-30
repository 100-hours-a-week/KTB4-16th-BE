package com.ktb4.team16.mulo.global.security;

import java.security.SecureRandom;
import java.util.Base64;

/** 테스트 Spring 컨텍스트에만 제공할, 실행마다 새로 생성되는 JWT 서명 키를 보관한다. */
final class TestJwtSecret {
    private static final int SECRET_BYTE_LENGTH = 32;
    private static final String VALUE = create();

    private TestJwtSecret() {
    }

    /** 최소 HS256 키 길이를 만족하는 테스트 전용 Base64 비밀값을 반환한다. */
    static String value() {
        return VALUE;
    }

    /** 테스트 실행마다 키를 새로 생성하여 저장소에 고정 키를 남기지 않는다. */
    private static String create() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
