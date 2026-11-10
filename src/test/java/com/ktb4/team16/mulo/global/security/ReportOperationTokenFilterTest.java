package com.ktb4.team16.mulo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.report.config.ReportOperationProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReportOperationTokenFilterTest {
    @Test
    void rejectsGenerationWithoutOperationToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        var filter = new ReportOperationTokenFilter(new ReportOperationProperties("token"),
                new SecurityErrorWriter());
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/internal/ops/monthly-reports/generate"),
                response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void passesGenerationWithOperationToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        var filter = new ReportOperationTokenFilter(new ReportOperationProperties("token"),
                new SecurityErrorWriter());
        var request = new MockHttpServletRequest("POST", "/internal/ops/monthly-reports/generate");
        request.addHeader("X-Report-Operation-Token", "token");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(same(request), any());
    }
    /** 신규 재요청·복구 경로도 운영 토큰이 없으면 실행 체인에 도달하지 않는다. */
    @org.junit.jupiter.api.Test
    void protectsNewRetryEndpoint() throws Exception {
        var filter = new ReportOperationTokenFilter(new ReportOperationProperties("token"),
                new SecurityErrorWriter());
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/internal/ops/monthly-reports/retry");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();
        filter.doFilter(request, response, chain);
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNull();
    }
}
