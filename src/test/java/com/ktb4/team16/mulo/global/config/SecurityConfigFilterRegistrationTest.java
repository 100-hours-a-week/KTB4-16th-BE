package com.ktb4.team16.mulo.global.config;

import com.ktb4.team16.mulo.global.security.ChatAuthVerifyApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigFilterRegistrationTest {

    @Test
    void keepsChatAuthVerifyFilterInSecurityChainWithoutServletRegistration() {
        ChatAuthVerifyApiKeyFilter filter = mock(ChatAuthVerifyApiKeyFilter.class);

        FilterRegistrationBean<ChatAuthVerifyApiKeyFilter> registration =
                new SecurityConfig().chatAuthVerifyApiKeyFilterServletRegistration(filter);

        assertThat(registration.getFilter()).isSameAs(filter);
        assertThat(registration.isEnabled()).isFalse();
    }
}
