package com.ktb4.team16.mulo.friend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.friend.dto.response.FriendListData;
import com.ktb4.team16.mulo.friend.dto.response.FriendListItemResponse;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestsData;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestItem;
import com.ktb4.team16.mulo.friend.dto.response.FriendUserResponse;
import com.ktb4.team16.mulo.friend.dto.response.SentFriendRequestsData;
import com.ktb4.team16.mulo.friend.dto.response.SentFriendRequestItem;
import com.ktb4.team16.mulo.friend.service.FriendCommandService;
import com.ktb4.team16.mulo.friend.service.FriendQueryService;
import com.ktb4.team16.mulo.friend.service.SendFriendRequestResult;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
class FriendControllerTest {

    @Mock
    private FriendCommandService commandService;
    @Mock
    private FriendQueryService queryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new FriendController(queryService),
                        new FriendRequestController(commandService, queryService),
                        new FriendshipController(commandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsFriendsPageAndForwardsCursorOptions() throws Exception {
        when(queryService.getFriends(1L, "next", 10)).thenReturn(new FriendListData(
                List.of(new FriendListItemResponse(5L, 2L, "친구")), null));

        mvc.perform(get("/api/users/me/friends").param("cursor", "next").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 목록 조회 성공"))
                .andExpect(jsonPath("$.data.friends[0].friendshipId").value(5))
                .andExpect(jsonPath("$.data.friends[0].userId").value(2))
                .andExpect(jsonPath("$.data.friends[0].nickname").value("친구"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        verify(queryService).getFriends(1L, "next", 10);
    }

    @Test
    void returnsReceivedAndSentRequestPages() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 6, 12, 0);
        when(queryService.getReceivedRequests(1L, null, null)).thenReturn(
                new ReceivedFriendRequestsData(List.of(new ReceivedFriendRequestItem(
                        7L, new FriendUserResponse(2L, "발신자"), createdAt)), null));
        when(queryService.getSentRequests(1L, null, null)).thenReturn(
                new SentFriendRequestsData(List.of(new SentFriendRequestItem(
                        8L, new FriendUserResponse(3L, "수신자"), createdAt)), null));

        mvc.perform(get("/api/users/me/friend-requests/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("받은 친구 요청 목록 조회 성공"))
                .andExpect(jsonPath("$.data.requests[0].requester.nickname").value("발신자"));
        mvc.perform(get("/api/users/me/friend-requests/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("보낸 친구 요청 목록 조회 성공"))
                .andExpect(jsonPath("$.data.requests[0].addressee.userId").value(3));

        verify(queryService).getReceivedRequests(1L, null, null);
        verify(queryService).getSentRequests(1L, null, null);
    }

    @Test
    void sendsPendingRequestWithCreatedStatus() throws Exception {
        when(commandService.sendFriendRequest(1L, "친구닉네임"))
                .thenReturn(new SendFriendRequestResult.Pending(11L));

        mvc.perform(post("/api/users/me/friend-requests")
                        .contentType("application/json")
                        .content("{\"nickname\":\"친구닉네임\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("친구 요청 성공"))
                .andExpect(jsonPath("$.data.friendRequestId").value(11))
                .andExpect(jsonPath("$.data.message").value("친구 요청을 보냈습니다."));
    }

    @Test
    void returnsOkWhenReverseRequestAutomaticallyCreatesFriendship() throws Exception {
        when(commandService.sendFriendRequest(1L, "친구닉네임"))
                .thenReturn(new SendFriendRequestResult.BecameFriends(12L));

        mvc.perform(post("/api/users/me/friend-requests")
                        .contentType("application/json")
                        .content("{\"nickname\":\"친구닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendshipId").value(12))
                .andExpect(jsonPath("$.data.message")
                        .value("서로의 친구 요청이 확인되어 친구가 되었습니다."));
    }

    @Test
    void mapsFriendshipConflictsToTheDocumentedConflictStatus() throws Exception {
        when(commandService.sendFriendRequest(1L, "친구닉네임"))
                .thenThrow(new FriendDomainException(ErrorCode.FRIENDSHIP_ALREADY_EXISTS));

        mvc.perform(post("/api/users/me/friend-requests")
                        .contentType("application/json")
                        .content("{\"nickname\":\"친구닉네임\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIENDSHIP_ALREADY_EXISTS"));
    }

    @Test
    void acceptsAndDeletesRequestsAndFriendship() throws Exception {
        when(commandService.acceptFriendRequest(1L, 11L)).thenReturn(13L);

        mvc.perform(post("/api/friend-requests/11/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendshipId").value(13));
        mvc.perform(delete("/api/friend-requests/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 요청이 삭제되었습니다."));
        mvc.perform(delete("/api/friendships/13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구가 삭제되었습니다."));

        verify(commandService).acceptFriendRequest(1L, 11L);
        verify(commandService).deleteFriendRequest(1L, 11L);
        verify(commandService).deleteFriendship(1L, 13L);
    }
}
