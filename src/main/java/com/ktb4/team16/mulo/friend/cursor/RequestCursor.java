package com.ktb4.team16.mulo.friend.cursor;

import java.time.LocalDateTime;

/** 요청 목록 페이지의 생성 시각 정렬 경계와 고유 요청 ID를 담는다. */
public record RequestCursor(LocalDateTime createdAt, Long friendRequestId) {
}
