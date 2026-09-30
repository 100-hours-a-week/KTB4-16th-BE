package com.ktb4.team16.mulo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SentryConfigurationTest {
    private final SentryOptions.BeforeSendCallback beforeSend =
            new SentryConfiguration().sentryBeforeSendCallback();

    @Test
    void removesQueryAndSensitiveRequestDataButKeepsMethodAndPath() {
        Request request = new Request();
        request.setMethod("GET");
        request.setUrl("https://api.example.test/api/weather?latitude=secret&longitude=secret");
        request.setQueryString("latitude=secret&longitude=secret");
        request.setData(Map.of("password", "secret"));
        request.setCookies("session=secret");
        request.setHeaders(Map.of("Authorization", "Bearer secret"));

        SentryEvent event = new SentryEvent(new RuntimeException("unexpected failure"));
        event.setRequest(request);
        event.setUser(new User());

        SentryEvent sanitized = beforeSend.execute(event, new Hint());

        assertThat(sanitized.getRequest().getMethod()).isEqualTo("GET");
        assertThat(sanitized.getRequest().getUrl()).isEqualTo("https://api.example.test/api/weather");
        assertThat(sanitized.getRequest().getQueryString()).isNull();
        assertThat(sanitized.getRequest().getData()).isNull();
        assertThat(sanitized.getRequest().getCookies()).isNull();
        assertThat(sanitized.getRequest().getHeaders()).isEmpty();
        assertThat(sanitized.getUser()).isNull();
        assertThat(sanitized.getThrowable()).isInstanceOf(RuntimeException.class);
    }
}
