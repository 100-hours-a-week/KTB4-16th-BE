package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.service.FriendRecordReadService;
import com.ktb4.team16.mulo.record.service.RecordService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendRecordReadServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private RecordService recordService;

    @Test
    void returnsFriendProfileAndAllActiveRecordRegions() {
        FriendRecordReadService service = createService();
        User friend = user(2L, "친구닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(friend));
        when(friendshipRepository.existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L)).thenReturn(true);
        when(recordService.getMyRecordRegions(2L)).thenReturn(List.of(
                new RecordRegionGroupResponse("1111010100", "청운동", 3L),
                new RecordRegionGroupResponse("UNKNOWN", "위치 정보 없음", 2L)));

        var dashboard = service.getDashboard(1L, 2L);

        assertThat(dashboard.userId()).isEqualTo(2L);
        assertThat(dashboard.nickname()).isEqualTo("친구닉네임");
        assertThat(dashboard.recordsCount()).isEqualTo(5L);
        assertThat(dashboard.regions()).hasSize(2);
        verify(friendshipRepository).existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L);
        verify(recordService).getMyRecordRegions(2L);
    }

    @Test
    void permitsAnEmptyDashboardForAnActiveFriend() {
        FriendRecordReadService service = createService();
        User friend = user(2L, "빈친구");
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(friend));
        when(friendshipRepository.existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L)).thenReturn(true);
        when(recordService.getMyRecordRegions(2L)).thenReturn(List.of());

        var dashboard = service.getDashboard(1L, 2L);

        assertThat(dashboard.recordsCount()).isZero();
        assertThat(dashboard.regions()).isEmpty();
    }

    @Test
    void checksCurrentFriendshipAgainBeforeReturningRegionRecords() {
        FriendRecordReadService service = createService();
        User friend = org.mockito.Mockito.mock(User.class);
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(friend));
        when(friendshipRepository.existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L)).thenReturn(true);
        RecordRegionRecordsData page = new RecordRegionRecordsData(
                "1111010100", "청운동", 1L, List.of(), null);
        when(recordService.getMyRecords(2L, "1111010100", null)).thenReturn(page);

        assertThat(service.getRegionRecords(1L, 2L, "1111010100", null)).isSameAs(page);

        verify(friendshipRepository).existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L);
        verify(recordService).getMyRecords(2L, "1111010100", null);
    }

    @Test
    void hidesNonFriendAndDeletedFriendDashboard() {
        FriendRecordReadService service = createService();
        User friend = org.mockito.Mockito.mock(User.class);
        when(userRepository.findByUserIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(friend));
        when(friendshipRepository.existsByUserLow_UserIdAndUserHigh_UserId(1L, 2L)).thenReturn(false);
        when(userRepository.findByUserIdAndDeletedAtIsNull(3L)).thenReturn(Optional.empty());

        assertFriendNotFound(() -> service.getDashboard(1L, 2L));
        assertFriendNotFound(() -> service.getRegionRecords(1L, 3L, "1111010100", null));

        verifyNoInteractions(recordService);
    }

    /** 친구 대시보드 조회 서비스의 의존성을 mock으로 구성한다. */
    private FriendRecordReadService createService() {
        return new FriendRecordReadService(userRepository, friendshipRepository, recordService);
    }

    /** 테스트에서 활성 친구의 사용자 프로필을 생성한다. */
    private static User user(Long userId, String nickname) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getNickname()).thenReturn(nickname);
        return user;
    }

    /** 친구가 아닌 사용자 응답이 같은 404 계약으로 숨겨지는지 확인한다. */
    private static void assertFriendNotFound(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(FriendDomainException.class)
                .extracting(error -> ((FriendDomainException) error).errorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
