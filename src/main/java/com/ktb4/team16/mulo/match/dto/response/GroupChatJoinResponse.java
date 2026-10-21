package com.ktb4.team16.mulo.match.dto.response;

import java.time.OffsetDateTime;

public record GroupChatJoinResponse(String message, Data data) {

    public record Data(
            Long chatRoomId,
            int roomSequence,
            boolean newlyAssigned,
            int admittedParticipantCount,
            int activeParticipantCount,
            OffsetDateTime expiresAt
    ) {
    }

    public static GroupChatJoinResponse from(ChatGroupJoinOrCreateResponse response) {
        return new GroupChatJoinResponse("그룹 채팅방 입장 성공", new Data(
                response.chatRoomId(), response.roomSequence(), response.newlyAssigned(),
                response.admittedParticipantCount(), response.activeParticipantCount(), response.expiresAt()));
    }
}
