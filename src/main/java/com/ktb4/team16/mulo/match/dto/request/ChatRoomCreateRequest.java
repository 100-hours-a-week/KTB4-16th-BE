package com.ktb4.team16.mulo.match.dto.request;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatRoomCreateRequest(Long matchId, OffsetDateTime expiresAt, List<Participant> participants) {
    public ChatRoomCreateRequest {
        if (matchId == null || matchId <= 0) {
            throw new IllegalArgumentException("matchId는 양수여야 합니다.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt은 필수입니다.");
        }
        if (participants == null || participants.size() != 2
                || participants.stream().anyMatch(participant -> participant == null)) {
            throw new IllegalArgumentException("참여자는 두 명이어야 합니다.");
        }
        participants = List.copyOf(participants);
    }

    public record Participant(Long userId, String nickname) {
        public Participant {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("userId는 양수여야 합니다.");
            }
            if (nickname == null || nickname.isBlank() || nickname.length() < 2 || nickname.length() > 10) {
                throw new IllegalArgumentException("nickname은 2자 이상 10자 이하여야 합니다.");
            }
        }
    }
}
