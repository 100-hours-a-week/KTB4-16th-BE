package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.GroupChatJoinRequest;
import com.ktb4.team16.mulo.match.dto.response.ChatGroupJoinOrCreateResponse;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class GroupChatEntryServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ChatGroupJoinOrCreateRequest INTERNAL_REQUEST =
            new ChatGroupJoinOrCreateRequest(LocalDate.of(2026, 10, 21), 12L, 34L, "nickname", "A");
    private final GroupChatEntryRequestFactory requestFactory = mock(GroupChatEntryRequestFactory.class);
    private final ChatRoomClient chatRoomClient = mock(ChatRoomClient.class);

    @Test
    void acceptsAtKstTransitionDateAndReturnsChatServerAssignment() {
        GroupChatEntryService service = service("2026-10-20T15:00:00Z");
        when(requestFactory.prepare(34L, 56L)).thenReturn(INTERNAL_REQUEST);
        when(chatRoomClient.joinOrCreateGroup(INTERNAL_REQUEST)).thenReturn(response(true));

        var result = service.join(34L, new GroupChatJoinRequest(56L));

        assertThat(result.data().chatRoomId()).isEqualTo(88L);
        assertThat(result.data().newlyAssigned()).isTrue();
        verify(chatRoomClient).joinOrCreateGroup(INTERNAL_REQUEST);
    }

    @Test
    void acceptsGroupEntryWithoutFeatureFlagOrStartDateConfiguration() {
        LocalDate groupDate = LocalDate.of(2026, 10, 22);
        ChatGroupJoinOrCreateRequest request = new ChatGroupJoinOrCreateRequest(
                groupDate, 12L, 34L, "nickname", "A");
        GroupChatEntryService service = service("2026-10-21T15:00:00Z");
        when(requestFactory.prepare(34L, 56L)).thenReturn(request);
        when(chatRoomClient.joinOrCreateGroup(request)).thenReturn(response(true));

        var result = service.join(34L, new GroupChatJoinRequest(56L));

        assertThat(result.data().chatRoomId()).isEqualTo(88L);
        verify(chatRoomClient).joinOrCreateGroup(request);
    }

    @Test
    void retriesSameIdempotentRequestAfterTransientFailure() {
        GroupChatEntryService service = service("2026-10-21T01:00:00+09:00");
        when(requestFactory.prepare(34L, 56L)).thenReturn(INTERNAL_REQUEST);
        when(chatRoomClient.joinOrCreateGroup(INTERNAL_REQUEST))
                .thenThrow(new ChatServerClientException(
                        ChatServerClientException.FailureType.COMMUNICATION_ERROR, null))
                .thenReturn(response(false));

        var result = service.join(34L, new GroupChatJoinRequest(56L));

        assertThat(result.data().newlyAssigned()).isFalse();
        verify(chatRoomClient, org.mockito.Mockito.times(2)).joinOrCreateGroup(INTERNAL_REQUEST);
    }

    @Test
    void doesNotSendPreviousDaysAssignmentIfKstMidnightPassedDuringPreparation() {
        GroupChatEntryService service = service("2026-10-21T15:00:00Z");
        when(requestFactory.prepare(34L, 56L)).thenReturn(INTERNAL_REQUEST);

        assertError(service, ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
        verify(chatRoomClient, never()).joinOrCreateGroup(INTERNAL_REQUEST);
    }

    @Test
    void doesNotRetryAfterExplicitLeaveAndReturnsReentryConflict() {
        GroupChatEntryService service = service("2026-10-21T01:00:00+09:00");
        when(requestFactory.prepare(34L, 56L)).thenReturn(INTERNAL_REQUEST);
        when(chatRoomClient.joinOrCreateGroup(INTERNAL_REQUEST)).thenThrow(new ChatServerClientException(
                ChatServerClientException.FailureType.GROUP_REENTRY_FORBIDDEN, 409));

        assertError(service, ErrorCode.GROUP_CHAT_REENTRY_FORBIDDEN);
        verify(chatRoomClient).joinOrCreateGroup(INTERNAL_REQUEST);
    }

    private GroupChatEntryService service(String now) {
        when(requestFactory.prepare(34L, 56L)).thenReturn(INTERNAL_REQUEST);
        return new GroupChatEntryService(requestFactory, chatRoomClient, Clock.fixed(Instant.parse(now), KST));
    }

    private ChatGroupJoinOrCreateResponse response(boolean newlyAssigned) {
        return new ChatGroupJoinOrCreateResponse(88L, 2, newlyAssigned, 7, 6,
                OffsetDateTime.parse("2026-10-22T00:00:00+09:00"));
    }

    private void assertError(GroupChatEntryService service, ErrorCode expected) {
        assertThatThrownBy(() -> service.join(34L, new GroupChatJoinRequest(56L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(expected);
    }

}
