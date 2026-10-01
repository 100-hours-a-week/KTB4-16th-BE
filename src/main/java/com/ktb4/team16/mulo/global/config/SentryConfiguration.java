package com.ktb4.team16.mulo.global.config;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfiguration {

    /** Removes request values that are not needed to diagnose server errors before sending. */
    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> sanitizeRequestData(event);
    }

    /** Keeps only request method and path while retaining the exception for diagnosis. */
    private SentryEvent sanitizeRequestData(SentryEvent event) {
        event.setUser(null);

        Request request = event.getRequest();
        if (request != null) {
            request.setQueryString(null);
            request.setData(null);
            request.setCookies(null);
            request.setHeaders(Collections.emptyMap());

            String url = request.getUrl();
            if (url != null) {
                int queryStart = url.indexOf('?');
                if (queryStart >= 0) {
                    request.setUrl(url.substring(0, queryStart));
                }
            }
        }

        return event;
    }
}
