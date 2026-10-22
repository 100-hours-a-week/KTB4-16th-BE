package com.ktb4.team16.mulo.match.dto.request;

import java.time.LocalDate;

/** Chat Server 내부 그룹 배정 API 계약. 값은 Backend의 오늘 질문·답변에서만 구성한다. */
public record ChatGroupJoinOrCreateRequest(
        LocalDate groupDate,
        Long voteQuestionId,
        Long userId,
        String nickname,
        String voteChoice
) {
}
