package com.ktb4.team16.mulo.user.event;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** RabbitMQ가 실제로 켜진 닉네임 MQ 경로일 때만 기존 HTTP 리스너를 비활성화한다. */
public final class ChatNicknameHttpSyncCondition implements Condition {

    /** 두 플래그가 모두 활성화된 경우에만 HTTP 동기화 bean을 제외한다. */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean nicknameDispatchEnabled = context.getEnvironment()
                .getProperty("mulo.nickname.dispatch.enabled", Boolean.class, false);
        boolean rabbitMqEnabled = context.getEnvironment()
                .getProperty("mulo.rabbitmq.enabled", Boolean.class, false);
        return !nicknameDispatchEnabled || !rabbitMqEnabled;
    }
}
