package com.ktb4.team16.mulo.friend.controller;

import com.ktb4.team16.mulo.friend.dto.response.FriendListResponse;
import com.ktb4.team16.mulo.friend.message.FriendMessage;
import com.ktb4.team16.mulo.friend.service.FriendQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 현재 사용자의 친구 목록 HTTP 요청을 조회 서비스로 전달한다. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class FriendController {

    private final FriendQueryService friendQueryService;

    /** 인증된 사용자의 cursor 기반 친구 목록을 반환한다. */
    @GetMapping("/friends")
    public FriendListResponse getFriends(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return new FriendListResponse(FriendMessage.FRIENDS_RETRIEVED,
                friendQueryService.getFriends(userId, cursor, size));
    }
}
