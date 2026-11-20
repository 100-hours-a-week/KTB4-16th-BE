package com.ktb4.team16.mulo.user.outbox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NicknameOutboxDispatcherTest {
    /** 한쪽 라우팅 실패 후에도 다른 큐로 발행하고 전체 이벤트를 재시도한다. */
    @Test
    void retriesWhenOnlyOneQueueConfirms() {
        var store = mock(ChatNicknameOutboxRepository.class);
        var publisher = mock(NicknamePublisher.class);
        var claim = new ChatNicknameOutboxRepository.Claim("event", "{}", "token", 1);
        when(store.claimNext()).thenReturn(Optional.of(claim), Optional.empty());
        when(publisher.send(claim, NicknameMqConfiguration.CHAT)).thenReturn(NicknamePublisher.Outcome.UNROUTABLE);
        when(publisher.send(claim, NicknameMqConfiguration.FRIEND)).thenReturn(NicknamePublisher.Outcome.CONFIRMED);
        new NicknameOutboxDispatcher(store, publisher).dispatch();
        verify(publisher).send(claim, NicknameMqConfiguration.FRIEND);
        verify(store).retry(claim, "CHAT_UNROUTABLE");
        verify(store, never()).published(claim);
    }

    /** 양쪽 발행이 확인된 이벤트만 완료 처리한다. */
    @Test
    void completesWhenBothQueuesConfirm() {
        var store = mock(ChatNicknameOutboxRepository.class);
        var publisher = mock(NicknamePublisher.class);
        var claim = new ChatNicknameOutboxRepository.Claim("event", "{}", "token", 1);
        when(store.claimNext()).thenReturn(Optional.of(claim), Optional.empty());
        when(publisher.send(eq(claim), anyString())).thenReturn(NicknamePublisher.Outcome.CONFIRMED);
        new NicknameOutboxDispatcher(store, publisher).dispatch();
        verify(store).published(claim);
    }
}
