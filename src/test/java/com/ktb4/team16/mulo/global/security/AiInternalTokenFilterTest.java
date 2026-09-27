package com.ktb4.team16.mulo.global.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import jakarta.servlet.FilterChain;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AiInternalTokenFilterTest {
    @Test
    void rejectsCallbackWithoutInternalToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        var filter = new AiInternalTokenFilter(properties(), new SecurityErrorWriter());
        var request = new MockHttpServletRequest("POST", "/internal/ai/report-ready");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void passesCallbackWithInternalToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        var filter = new AiInternalTokenFilter(properties(), new SecurityErrorWriter());
        var request = new MockHttpServletRequest("POST", "/internal/ai/report-ready");
        request.addHeader("X-Internal-Token", "token");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.any());
    }

    private AiProperties properties() {
        return new AiProperties(URI.create("http://ai.test"), "token", Duration.ofSeconds(1),
                Duration.ofSeconds(1), false);
    }
}
