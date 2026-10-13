package com.ktb4.team16.mulo.friend.controller;

import com.ktb4.team16.mulo.friend.dto.response.FriendActionResponse;
import com.ktb4.team16.mulo.friend.message.FriendMessage;
import com.ktb4.team16.mulo.friend.service.FriendCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 친구 관계 삭제 HTTP 경로를 담당한다. */
@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendCommandService friendCommandService;

    /** 관계 구성원만 현재 friendship 행을 삭제한다. */
    @DeleteMapping("/{friendshipId}")
    public FriendActionResponse deleteFriendship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendshipId
    ) {
        friendCommandService.deleteFriendship(userId, friendshipId);
        return new FriendActionResponse(FriendMessage.FRIENDSHIP_DELETED);
    }
}
