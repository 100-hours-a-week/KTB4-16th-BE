package com.ktb4.team16.mulo.notification.outbox;

import java.time.Duration;

/** Outbox 추가 재시도 간격과 횟수 제한을 계산한다. */
public class NotificationRetryPolicy {

    public static final int MAX_ADDITIONAL_RETRIES = 5;
    public static final int MAX_JITTER_SECONDS = 30;

    /** 저장된 재시도 횟수에 맞춰 확정된 지수 간격과 지터를 계산한다. */
    public Duration delayForFailure(int retryCount, int jitterSeconds) {
        if (retryCount < 0 || retryCount >= MAX_ADDITIONAL_RETRIES) {
            throw new IllegalArgumentException("재시도 횟수가 허용 범위를 벗어났습니다.");
        }
        if (jitterSeconds < 0 || jitterSeconds > MAX_JITTER_SECONDS) {
            throw new IllegalArgumentException("지터는 0~30초 범위여야 합니다.");
        }

        long delayMinutes = 1L << retryCount;
        return Duration.ofMinutes(delayMinutes).plusSeconds(jitterSeconds);
    }
}
