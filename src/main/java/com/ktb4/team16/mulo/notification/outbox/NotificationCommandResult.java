package com.ktb4.team16.mulo.notification.outbox;

import java.util.List;
import java.util.UUID;

/** 원본 트랜잭션 결과와 그 트랜잭션에서 저장한 Outbox ID를 함께 전달한다. */
public record NotificationCommandResult<T>(T result, List<UUID> eventIds) {

    /** 후속 처리 단계에서 목록이 바뀌지 않도록 이벤트 ID를 복사한다. */
    public NotificationCommandResult {
        eventIds = List.copyOf(eventIds);
    }
}
