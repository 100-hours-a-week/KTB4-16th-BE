package com.ktb4.team16.mulo.match.dto.response;

import java.time.OffsetDateTime;

/** Chat Server의 raw JSON 응답 DTO. */
public record ChatGroupJoinOrCreateResponse(
        Long chatRoomId,
        int roomSequence,
        boolean newlyAssigned,
        int admittedParticipantCount,
        int activeParticipantCount,
        OffsetDateTime expiresAt
) {
}
