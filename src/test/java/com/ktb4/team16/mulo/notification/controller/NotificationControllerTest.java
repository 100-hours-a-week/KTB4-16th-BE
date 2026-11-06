package com.ktb4.team16.mulo.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.notification.dto.NotificationUnreadStatusResponse;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock
    private NotificationService notificationService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unreadStatusUsesAuthenticatedUserInsteadOfRequestParameter() throws Exception {
        when(notificationService.getUnreadStatus(35L)).thenReturn(
                new NotificationUnreadStatusResponse("미읽음 알림 여부 조회 성공",
                        new NotificationUnreadStatusResponse.Data(true)));

        mvc.perform(get("/api/notifications/unread-status").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미읽음 알림 여부 조회 성공"))
                .andExpect(jsonPath("$.data.hasUnread").value(true));

        verify(notificationService).getUnreadStatus(35L);
    }
}
