package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationRetryPolicyTest {

    /** 최초 실패 뒤 1분부터 다섯 번째 재시도 뒤 16분까지 지수 간격을 적용하는지 검증한다. */
    @Test
    void usesApprovedExponentialIntervalsForEachRetryCount() {
        NotificationRetryPolicy policy = new NotificationRetryPolicy();

        assertThat(policy.delayForFailure(0, 0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayForFailure(1, 0)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayForFailure(2, 0)).isEqualTo(Duration.ofMinutes(4));
        assertThat(policy.delayForFailure(3, 0)).isEqualTo(Duration.ofMinutes(8));
        assertThat(policy.delayForFailure(4, 0)).isEqualTo(Duration.ofMinutes(16));
    }

    /** 사용자 확정 지터 0~30초가 재시도 간격에 더해지는지 검증한다. */
    @Test
    void addsJitterWithinTheApprovedThirtySecondRange() {
        NotificationRetryPolicy policy = new NotificationRetryPolicy();

        assertThat(policy.delayForFailure(0, 0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayForFailure(0, 30)).isEqualTo(Duration.ofSeconds(90));
    }

    /** 허용하지 않은 retry count와 지터가 잘못된 상태로 숨겨지지 않는지 검증한다. */
    @Test
    void rejectsRetryCountsAndJitterOutsideThePolicy() {
        NotificationRetryPolicy policy = new NotificationRetryPolicy();

        assertThatThrownBy(() -> policy.delayForFailure(5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayForFailure(0, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
