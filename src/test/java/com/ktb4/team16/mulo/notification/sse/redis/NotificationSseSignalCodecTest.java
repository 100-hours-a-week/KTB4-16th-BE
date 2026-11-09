package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NotificationSseSignalCodecTest {

    private final NotificationSseSignalCodec codec = new NotificationSseSignalCodec(new ObjectMapper());

    @Test
    void roundTripsRecipientWithoutNotificationDetails() {
        NotificationSseSignal signal = NotificationSseSignal.forRecipient(42L);

        String encoded = codec.encode(signal);

        assertThat(encoded).contains("\"version\":1", "\"userId\":42");
        assertThat(encoded).doesNotContain("notificationId", "token", "content");
        assertThat(codec.decode(encoded)).contains(signal);
    }

    @Test
    void rejectsUnsupportedVersionAndInvalidRecipient() {
        assertThat(codec.decode("{\"version\":2,\"userId\":42}")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":0}")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":-1}")).isEmpty();
        assertThatThrownBy(() -> new NotificationSseSignal(2, 42L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationSseSignal.forRecipient(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingExtraAndCoercedFields() {
        assertThat(codec.decode("{\"version\":1}")).isEmpty();
        assertThat(codec.decode("{\"userId\":42}")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":42,\"token\":\"secret\"}"))
                .isEmpty();
        assertThat(codec.decode("{\"version\":\"1\",\"userId\":42}")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":\"42\"}")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":9223372036854775808}"))
                .isEmpty();
    }

    @Test
    void rejectsMalformedAndOversizedMessages() {
        assertThat(codec.decode(null)).isEmpty();
        assertThat(codec.decode(" ")).isEmpty();
        assertThat(codec.decode("not-json")).isEmpty();
        assertThat(codec.decode("[]")).isEmpty();
        assertThat(codec.decode("{\"version\":1,\"userId\":42,\"padding\":\""
                + "x".repeat(NotificationSseSignalCodec.MAX_MESSAGE_BYTES) + "\"}")).isEmpty();
        assertThat(codec.decode("가".repeat(NotificationSseSignalCodec.MAX_MESSAGE_BYTES / 2)))
                .isEmpty();
        assertThat(codec.encode(NotificationSseSignal.forRecipient(Long.MAX_VALUE))
                .getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(NotificationSseSignalCodec.MAX_MESSAGE_BYTES);
    }
}
