package com.ktb4.team16.mulo.user.event;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.match.client.ChatServerClientException.FailureType;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatNicknameSyncAfterCommitListenerTest {
    private static final Long USER_ID = 17L;

    @Mock UserProfileService userProfileService;
    @Mock ChatRoomClient chatRoomClient;
    ChatNicknameSyncAfterCommitListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatNicknameSyncAfterCommitListener(userProfileService, chatRoomClient);
    }

    @Test
    void synchronizesLatestNicknameAfterReceivingCommittedEvent() {
        when(userProfileService.getCurrentNicknameForChatSync(USER_ID)).thenReturn("새닉네임");

        listener.handle(new UserNicknameChangedEvent(USER_ID));

        InOrder order = inOrder(userProfileService, chatRoomClient);
        order.verify(userProfileService).getCurrentNicknameForChatSync(USER_ID);
        order.verify(chatRoomClient).updateParticipantNickname(USER_ID, "새닉네임");
    }

    @Test
    void reloadsLatestNicknameBeforeEachRetry() {
        when(userProfileService.getCurrentNicknameForChatSync(USER_ID))
                .thenReturn("중간닉네임", "최신닉네임");
        when(chatRoomClient.updateParticipantNickname(USER_ID, "중간닉네임"))
                .thenThrow(new ChatServerClientException(FailureType.SERVER_ERROR, 503));

        listener.handle(new UserNicknameChangedEvent(USER_ID));

        InOrder order = inOrder(userProfileService, chatRoomClient);
        order.verify(userProfileService).getCurrentNicknameForChatSync(USER_ID);
        order.verify(chatRoomClient).updateParticipantNickname(USER_ID, "중간닉네임");
        order.verify(userProfileService).getCurrentNicknameForChatSync(USER_ID);
        order.verify(chatRoomClient).updateParticipantNickname(USER_ID, "최신닉네임");
    }

    @Test
    void doesNotRetryAuthenticationOrClientFailures() {
        when(userProfileService.getCurrentNicknameForChatSync(USER_ID)).thenReturn("새닉네임");
        when(chatRoomClient.updateParticipantNickname(USER_ID, "새닉네임"))
                .thenThrow(new ChatServerClientException(FailureType.AUTHENTICATION_ERROR, 401));

        listener.handle(new UserNicknameChangedEvent(USER_ID));

        verify(userProfileService).getCurrentNicknameForChatSync(USER_ID);
        verify(chatRoomClient).updateParticipantNickname(USER_ID, "새닉네임");
    }

    @Test
    void logsAndStopsWhenLatestNicknameCannotBeRead() {
        when(userProfileService.getCurrentNicknameForChatSync(USER_ID))
                .thenThrow(new IllegalStateException("lookup failed"));

        listener.handle(new UserNicknameChangedEvent(USER_ID));

        verify(chatRoomClient, never()).updateParticipantNickname(USER_ID, "새닉네임");
    }
}
