package com.ktb4.team16.mulo.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.friend.cursor.FriendCursorCodec;
import com.ktb4.team16.mulo.friend.cursor.RequestCursorCodec;
import com.ktb4.team16.mulo.friend.dto.response.FriendListData;
import com.ktb4.team16.mulo.friend.dto.response.ReceivedFriendRequestsData;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.friend.repository.FriendListRow;
import com.ktb4.team16.mulo.friend.repository.FriendRequestListRow;
import com.ktb4.team16.mulo.friend.repository.FriendRequestRepository;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FriendQueryServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Test
    void returnsOnlyRequestedFriendsAndBuildsCursorFromLastVisibleItem() {
        FriendQueryService service = createService();
        FriendListRow first = friend(11L, 21L, "가나다");
        FriendListRow overflow = org.mockito.Mockito.mock(FriendListRow.class);
        when(friendshipRepository.findFriendsFirstPage(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(first, overflow));

        FriendListData page = service.getFriends(1L, null, 1);

        assertThat(page.friends()).hasSize(1);
        assertThat(page.friends().getFirst().nickname()).isEqualTo("가나다");
        assertThat(new FriendCursorCodec().decode(page.nextCursor()).userId()).isEqualTo(21L);
        verify(friendshipRepository).findFriendsFirstPage(eq(1L), any(Pageable.class));
    }

    @Test
    void receivedRequestCursorUsesTimestampAndRequestIdTieBreaker() {
        FriendQueryService service = createService();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 6, 12, 30);
        FriendRequestListRow first = request(31L, 21L, "가나다", createdAt);
        FriendRequestListRow overflow = org.mockito.Mockito.mock(FriendRequestListRow.class);
        when(friendRequestRepository.findReceivedRequestsFirstPage(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(first, overflow));

        ReceivedFriendRequestsData firstPage = service.getReceivedRequests(1L, null, 1);
        FriendRequestListRow nextRow = request(30L, 22L, "라마바", createdAt);
        when(friendRequestRepository.findReceivedRequestsAfterCursor(
                eq(1L), eq(createdAt), eq(31L), any(Pageable.class)))
                .thenReturn(List.of(nextRow));

        ReceivedFriendRequestsData secondPage = service.getReceivedRequests(
                1L, firstPage.nextCursor(), 1);

        assertThat(firstPage.requests()).extracting(item -> item.friendRequestId())
                .containsExactly(31L);
        assertThat(secondPage.requests()).extracting(item -> item.friendRequestId())
                .containsExactly(30L);
        assertThat(secondPage.nextCursor()).isNull();
        verify(friendRequestRepository).findReceivedRequestsAfterCursor(
                eq(1L), eq(createdAt), eq(31L), any(Pageable.class));
    }

    @Test
    void rejectsPageSizesOutsideTheContract() {
        FriendQueryService service = createService();

        assertFriendError(() -> service.getFriends(1L, null, 0), "INVALID_SIZE");
        assertFriendError(() -> service.getReceivedRequests(1L, null, 101), "INVALID_SIZE");
    }

    /** 조회 서비스와 cursor codec을 기존 repository mock으로 구성한다. */
    private FriendQueryService createService() {
        return new FriendQueryService(friendshipRepository, friendRequestRepository,
                new FriendCursorCodec(), new RequestCursorCodec());
    }

    /** 테스트용 친구 projection을 준비한다. */
    private static FriendListRow friend(Long friendshipId, Long userId, String nickname) {
        FriendListRow row = org.mockito.Mockito.mock(FriendListRow.class);
        when(row.getFriendshipId()).thenReturn(friendshipId);
        when(row.getUserId()).thenReturn(userId);
        when(row.getNickname()).thenReturn(nickname);
        return row;
    }

    /** 테스트용 친구 요청 projection을 준비한다. */
    private static FriendRequestListRow request(
            Long requestId,
            Long userId,
            String nickname,
            LocalDateTime createdAt
    ) {
        FriendRequestListRow row = org.mockito.Mockito.mock(FriendRequestListRow.class);
        when(row.getFriendRequestId()).thenReturn(requestId);
        when(row.getUserId()).thenReturn(userId);
        when(row.getNickname()).thenReturn(nickname);
        when(row.getCreatedAt()).thenReturn(createdAt);
        return row;
    }

    /** 잘못된 페이지 크기가 계약 오류 코드로 반환되는지 확인한다. */
    private static void assertFriendError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String expectedCode
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(FriendDomainException.class)
                .extracting(error -> ((FriendDomainException) error).errorCode().name())
                .isEqualTo(expectedCode);
    }
}
