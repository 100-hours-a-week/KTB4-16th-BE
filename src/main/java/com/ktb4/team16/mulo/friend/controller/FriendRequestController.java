package com.ktb4.team16.mulo.friend.controller;

import com.ktb4.team16.mulo.friend.dto.request.SendFriendRequestRequest;
import com.ktb4.team16.mulo.friend.dto.response.FriendActionResponse;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestAcceptedData;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestAcceptedResponse;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestAutoAcceptedData;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestAutoAcceptedResponse;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestCreatedData;
import com.ktb4.team16.mulo.friend.dto.response.FriendRequestCreatedResponse;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestsResponse;
import com.ktb4.team16.mulo.friend.dto.response.SentFriendRequestsResponse;
import com.ktb4.team16.mulo.friend.message.FriendMessage;
import com.ktb4.team16.mulo.friend.service.FriendCommandService;
import com.ktb4.team16.mulo.friend.service.FriendQueryService;
import com.ktb4.team16.mulo.friend.service.SendFriendRequestResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 받은·보낸 요청 조회와 요청 생성·수락·삭제 HTTP 경로를 담당한다. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendCommandService friendCommandService;
    private final FriendQueryService friendQueryService;

    /** 현재 사용자가 받은 pending 친구 요청 페이지를 반환한다. */
    @GetMapping("/users/me/friend-requests/received")
    public ReceivedFriendRequestsResponse getReceivedRequests(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return new ReceivedFriendRequestsResponse(FriendMessage.RECEIVED_REQUESTS_RETRIEVED,
                friendQueryService.getReceivedRequests(userId, cursor, size));
    }

    /** 현재 사용자가 보낸 pending 친구 요청 페이지를 반환한다. */
    @GetMapping("/users/me/friend-requests/sent")
    public SentFriendRequestsResponse getSentRequests(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return new SentFriendRequestsResponse(FriendMessage.SENT_REQUESTS_RETRIEVED,
                friendQueryService.getSentRequests(userId, cursor, size));
    }

    /** 닉네임으로 요청을 만들고 자동 수락 결과는 200, 새 요청은 201로 변환한다. */
    @PostMapping("/users/me/friend-requests")
    public ResponseEntity<Object> sendFriendRequest(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SendFriendRequestRequest request
    ) {
        SendFriendRequestResult result = friendCommandService.sendFriendRequest(
                userId, request.nickname());
        if (result instanceof SendFriendRequestResult.Pending pending) {
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new FriendRequestCreatedResponse("친구 요청 성공",
                            new FriendRequestCreatedData(
                                    pending.friendRequestId(), FriendMessage.REQUEST_SENT)));
        }

        SendFriendRequestResult.BecameFriends becameFriends =
                (SendFriendRequestResult.BecameFriends) result;
        return ResponseEntity.ok(new FriendRequestAutoAcceptedResponse("친구 요청 성공",
                new FriendRequestAutoAcceptedData(
                        becameFriends.friendshipId(), FriendMessage.REQUEST_AUTO_ACCEPTED)));
    }

    /** 수신자 권한을 확인한 뒤 요청을 수락하고 새 friendship ID를 반환한다. */
    @PostMapping("/friend-requests/{friendRequestId}/accept")
    public FriendRequestAcceptedResponse acceptFriendRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendRequestId
    ) {
        Long friendshipId = friendCommandService.acceptFriendRequest(userId, friendRequestId);
        return new FriendRequestAcceptedResponse("친구 요청 수락 성공",
                new FriendRequestAcceptedData(friendshipId, FriendMessage.REQUEST_ACCEPTED));
    }

    /** 요청 당사자가 처리 전 친구 요청을 거절하거나 취소한다. */
    @DeleteMapping("/friend-requests/{friendRequestId}")
    public FriendActionResponse deleteFriendRequest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long friendRequestId
    ) {
        friendCommandService.deleteFriendRequest(userId, friendRequestId);
        return new FriendActionResponse(FriendMessage.REQUEST_DELETED);
    }
}
