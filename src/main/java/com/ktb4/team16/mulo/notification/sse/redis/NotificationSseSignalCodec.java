package com.ktb4.team16.mulo.notification.sse.redis;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class NotificationSseSignalCodec {

    public static final int MAX_MESSAGE_BYTES = 256;

    private final ObjectMapper objectMapper;

    public NotificationSseSignalCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(NotificationSseSignal signal) {
        if (signal == null) {
            throw new IllegalArgumentException("SSE 알림 신호가 필요합니다.");
        }
        try {
            String message = objectMapper.writeValueAsString(signal);
            if (message.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("SSE 알림 신호의 크기가 허용 범위를 초과했습니다.");
            }
            return message;
        } catch (JacksonException exception) {
            throw new IllegalStateException("SSE 알림 신호를 직렬화하지 못했습니다.", exception);
        }
    }

    public Optional<NotificationSseSignal> decode(String message) {
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_BYTES
                || message.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root == null || !root.isObject() || root.size() != 2) {
                return Optional.empty();
            }
            JsonNode version = root.path("version");
            JsonNode userId = root.path("userId");
            if (!version.isInt() || version.intValue() != NotificationSseSignal.CURRENT_VERSION
                    || !userId.isIntegralNumber() || !userId.canConvertToLong()
                    || userId.longValue() <= 0) {
                return Optional.empty();
            }
            return Optional.of(new NotificationSseSignal(version.intValue(), userId.longValue()));
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }
}
