package com.ktb4.team16.mulo.friend.entity;

/** 두 사용자 ID를 DB의 low/high 쌍 기준으로 정규화한다. */
public record UserPair(Long lowId, Long highId) {

    /** 잘못된 ID나 역순 쌍이 영속 계층에 전달되지 않게 검증한다. */
    public UserPair {
        if (lowId == null || highId == null || lowId <= 0 || highId <= 0 || lowId >= highId) {
            throw new IllegalArgumentException("두 개의 서로 다른 양수 사용자 ID가 필요합니다.");
        }
    }

    /** 두 ID를 작은 값부터 정렬하고 자기 자신 쌍을 거부한다. */
    public static UserPair of(Long firstUserId, Long secondUserId) {
        if (firstUserId == null || secondUserId == null
                || firstUserId <= 0 || secondUserId <= 0
                || firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException("두 개의 서로 다른 양수 사용자 ID가 필요합니다.");
        }

        return firstUserId < secondUserId
                ? new UserPair(firstUserId, secondUserId)
                : new UserPair(secondUserId, firstUserId);
    }
}
