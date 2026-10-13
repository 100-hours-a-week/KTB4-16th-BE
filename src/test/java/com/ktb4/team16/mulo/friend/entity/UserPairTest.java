package com.ktb4.team16.mulo.friend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class UserPairTest {

    private static final String USER_PAIR_CLASS =
            "com.ktb4.team16.mulo.friend.entity.UserPair";

    @Test
    void normalizesUserIdsIntoAscendingOrder() throws Exception {
        Class<?> pairType = Class.forName(USER_PAIR_CLASS);
        Method factory = pairType.getMethod("of", Long.class, Long.class);
        Object pair = factory.invoke(null, 35L, 12L);

        assertThat(pairType.getMethod("lowId").invoke(pair)).isEqualTo(12L);
        assertThat(pairType.getMethod("highId").invoke(pair)).isEqualTo(35L);
    }

    @Test
    void rejectsTheSameUserAsBothSidesOfThePair() {
        assertThatThrownBy(() -> {
            Class<?> pairType = Class.forName(USER_PAIR_CLASS);
            pairType.getMethod("of", Long.class, Long.class).invoke(null, 12L, 12L);
        })
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
