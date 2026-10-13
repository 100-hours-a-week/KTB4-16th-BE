package com.ktb4.team16.mulo.friend.service;

import com.ktb4.team16.mulo.friend.cursor.FriendCursor;
import com.ktb4.team16.mulo.friend.cursor.FriendCursorCodec;
import com.ktb4.team16.mulo.friend.cursor.RequestCursor;
import com.ktb4.team16.mulo.friend.cursor.RequestCursorCodec;
import com.ktb4.team16.mulo.friend.dto.response.FriendListData;
import com.ktb4.team16.mulo.friend.dto.response.FriendListItemResponse;
import com.ktb4.team16.mulo.friend.dto.response.FriendUserResponse;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestItem;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestsData;
import com.ktb4.team16.mulo.friend.dto.response.SentFriendRequestItem;
import com.ktb4.team16.mulo.friend.dto.response.SentFriendRequestsData;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.friend.repository.FriendListRow;
import com.ktb4.team16.mulo.friend.repository.FriendRequestListRow;
import com.ktb4.team16.mulo.friend.repository.FriendRequestRepository;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 친구·요청 목록을 정렬 계약과 keyset cursor 규칙에 맞춰 조립한다. */
@Service
@RequiredArgsConstructor
public class FriendQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendCursorCodec friendCursorCodec;
    private final RequestCursorCodec requestCursorCodec;

    /** 닉네임·사용자 ID 오름차순 친구 페이지를 반환한다. */
    @Transactional(readOnly = true)
    public FriendListData getFriends(Long userId, String cursor, Integer size) {
        int pageSize = pageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<FriendListRow> rows;
        if (cursor == null) {
            rows = friendshipRepository.findFriendsFirstPage(userId, pageable);
        } else {
            FriendCursor boundary = friendCursorCodec.decode(cursor);
            rows = friendshipRepository.findFriendsAfterCursor(
                    userId, boundary.nickname(), boundary.userId(), pageable);
        }

        boolean hasNext = rows.size() > pageSize;
        List<FriendListRow> visibleRows = rows.subList(0, Math.min(pageSize, rows.size()));
        List<FriendListItemResponse> friends = visibleRows.stream()
                .map(row -> new FriendListItemResponse(
                        row.getFriendshipId(), row.getUserId(), row.getNickname()))
                .toList();
        String nextCursor = hasNext
                ? friendCursorCodec.encode(
                        visibleRows.get(visibleRows.size() - 1).getNickname(),
                        visibleRows.get(visibleRows.size() - 1).getUserId())
                : null;
        return new FriendListData(friends, nextCursor);
    }

    /** 생성 시각·요청 ID 내림차순 받은 요청 페이지를 반환한다. */
    @Transactional(readOnly = true)
    public ReceivedFriendRequestsData getReceivedRequests(Long userId, String cursor, Integer size) {
        int pageSize = pageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<FriendRequestListRow> rows;
        if (cursor == null) {
            rows = friendRequestRepository.findReceivedRequestsFirstPage(userId, pageable);
        } else {
            RequestCursor boundary = requestCursorCodec.decode(cursor);
            rows = friendRequestRepository.findReceivedRequestsAfterCursor(
                    userId, boundary.createdAt(), boundary.friendRequestId(), pageable);
        }

        boolean hasNext = rows.size() > pageSize;
        List<FriendRequestListRow> visibleRows = rows.subList(0, Math.min(pageSize, rows.size()));
        List<ReceivedFriendRequestItem> requests = visibleRows.stream()
                .map(row -> new ReceivedFriendRequestItem(
                        row.getFriendRequestId(),
                        new FriendUserResponse(row.getUserId(), row.getNickname()),
                        row.getCreatedAt()))
                .toList();
        return new ReceivedFriendRequestsData(requests,
                nextRequestCursor(visibleRows, hasNext));
    }

    /** 생성 시각·요청 ID 내림차순 보낸 요청 페이지를 반환한다. */
    @Transactional(readOnly = true)
    public SentFriendRequestsData getSentRequests(Long userId, String cursor, Integer size) {
        int pageSize = pageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        List<FriendRequestListRow> rows;
        if (cursor == null) {
            rows = friendRequestRepository.findSentRequestsFirstPage(userId, pageable);
        } else {
            RequestCursor boundary = requestCursorCodec.decode(cursor);
            rows = friendRequestRepository.findSentRequestsAfterCursor(
                    userId, boundary.createdAt(), boundary.friendRequestId(), pageable);
        }

        boolean hasNext = rows.size() > pageSize;
        List<FriendRequestListRow> visibleRows = rows.subList(0, Math.min(pageSize, rows.size()));
        List<SentFriendRequestItem> requests = visibleRows.stream()
                .map(row -> new SentFriendRequestItem(
                        row.getFriendRequestId(),
                        new FriendUserResponse(row.getUserId(), row.getNickname()),
                        row.getCreatedAt()))
                .toList();
        return new SentFriendRequestsData(requests,
                nextRequestCursor(visibleRows, hasNext));
    }

    /** 공통 요청 페이지 경계에서 다음 cursor 문자열을 만든다. */
    private String nextRequestCursor(List<FriendRequestListRow> visibleRows, boolean hasNext) {
        if (!hasNext || visibleRows.isEmpty()) {
            return null;
        }
        FriendRequestListRow last = visibleRows.get(visibleRows.size() - 1);
        return requestCursorCodec.encode(last.getCreatedAt(), last.getFriendRequestId());
    }

    /** 요청 크기의 기본값과 1~100 범위를 검증한다. */
    private int pageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_SIZE;
        }
        if (requestedSize < 1 || requestedSize > MAX_SIZE) {
            throw new FriendDomainException(ErrorCode.INVALID_SIZE);
        }
        return requestedSize;
    }
}
