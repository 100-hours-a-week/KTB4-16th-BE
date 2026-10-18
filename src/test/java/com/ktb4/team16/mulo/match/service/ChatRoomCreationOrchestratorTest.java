package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.match.client.ChatServerClientException.FailureType;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest.Participant;
import com.ktb4.team16.mulo.match.dto.response.ChatRoomCreateResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatRoomCreationOrchestratorTest {

    private final ChatRoomClient client = mock(ChatRoomClient.class);
    private final MatchCompletionService completionService = mock(MatchCompletionService.class);
    private final ChatRoomCreationOrchestrator orchestrator = new ChatRoomCreationOrchestrator(client,
            completionService);
    private final ChatRoomCreateRequest request = new ChatRoomCreateRequest(41L,
            OffsetDateTime.parse("2026-10-18T00:00:00+09:00"),
            List.of(new Participant(11L, "user11"), new Participant(12L, "user12")));

    @Test
    void retriesRetryableFailureWithTheSameRequestAndCompletesOnThirdAttempt() {
        when(client.createChatRoom(request))
                .thenThrow(new ChatServerClientException(FailureType.COMMUNICATION_ERROR, null))
                .thenThrow(new ChatServerClientException(FailureType.SERVER_ERROR, 503))
                .thenReturn(new ChatRoomCreateResponse(91L));

        orchestrator.createAfterMatch(41L, request);

        ArgumentCaptor<ChatRoomCreateRequest> captor = ArgumentCaptor.forClass(ChatRoomCreateRequest.class);
        verify(client, times(3)).createChatRoom(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(value -> assertThat(value).isSameAs(request));
        verify(completionService).complete(41L, 91L);
    }

    @Test
    void immediatelyFailsForNonRetryableConflict() {
        when(client.createChatRoom(request))
                .thenThrow(new ChatServerClientException(FailureType.REQUEST_CONFLICT, 409));

        orchestrator.createAfterMatch(41L, request);

        verify(client).createChatRoom(request);
        verify(completionService).fail(41L);
    }

    @Test
    void marksMatchFailedAfterThreeRetryableFailures() {
        when(client.createChatRoom(request))
                .thenThrow(new ChatServerClientException(FailureType.SERVER_ERROR, 503));

        orchestrator.createAfterMatch(41L, request);

        verify(client, times(3)).createChatRoom(request);
        verify(completionService).fail(41L);
    }
}
