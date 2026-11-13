package com.ktb4.team16.mulo.notification.sse.redis;

import java.util.Objects;

public final class NotificationSseRedisPublisher {

    private final NotificationSseRedisTransport transport;

    public NotificationSseRedisPublisher(NotificationSseRedisTransport transport) {
        this.transport = Objects.requireNonNull(transport);
    }

    public long publish(long recipientUserId) {
        return transport.publish(NotificationSseSignal.forRecipient(recipientUserId));
    }
}
