package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class NotificationSseRedisPublisherTest {

    private final NotificationSseRedisTransport transport = mock(NotificationSseRedisTransport.class);
    private final NotificationSseRedisPublisher publisher = new NotificationSseRedisPublisher(transport);

    @Test
    void publishesOnlyRecipientSignal() {
        NotificationSseSignal signal = NotificationSseSignal.forRecipient(42L);
        when(transport.publish(signal)).thenReturn(2L);

        assertThat(publisher.publish(42L)).isEqualTo(2L);

        verify(transport).publish(signal);
    }

    @Test
    void rejectsInvalidRecipientBeforeRedisCall() {
        assertThatThrownBy(() -> publisher.publish(0L))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verifyNoInteractions(transport);
    }

    @Test
    void exposesRedisFailureToAfterCommitCaller() {
        NotificationSseSignal signal = NotificationSseSignal.forRecipient(42L);
        when(transport.publish(signal)).thenThrow(new IllegalStateException("Redis 발행 실패"));

        assertThatThrownBy(() -> publisher.publish(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 발행 실패");
    }
}
