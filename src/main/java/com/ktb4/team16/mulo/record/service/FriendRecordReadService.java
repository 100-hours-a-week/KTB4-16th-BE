package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.friend.entity.UserPair;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.record.dto.response.FriendRecordDashboardData;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 친구 관계를 확인한 뒤 친구의 활성 자물쇠 요약과 지역 페이지를 조회한다. */
@Service
@RequiredArgsConstructor
public class FriendRecordReadService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final RecordService recordService;

    /** 친구의 현재 프로필과 지역별 활성 자물쇠 수를 반환한다. */
    @Transactional(readOnly = true)
    public FriendRecordDashboardData getDashboard(Long viewerId, Long friendUserId) {
        User friend = requireCurrentFriend(viewerId, friendUserId);
        List<RecordRegionGroupResponse> regions = recordService.getMyRecordRegions(friendUserId);
        long recordsCount = regions.stream()
                .mapToLong(RecordRegionGroupResponse::recordsCount)
                .sum();
        return new FriendRecordDashboardData(
                friend.getUserId(), friend.getNickname(), recordsCount, regions);
    }

    /** 친구 관계를 매 요청 확인한 뒤 친구의 지역 커서 페이지를 반환한다. */
    @Transactional(readOnly = true)
    public RecordRegionRecordsData getRegionRecords(
            Long viewerId,
            Long friendUserId,
            String legalDongCode,
            String cursor
    ) {
        requireCurrentFriend(viewerId, friendUserId);
        return recordService.getMyRecords(friendUserId, legalDongCode, cursor);
    }

    /** 대상 사용자의 활성 상태와 현재 정규화 친구 관계를 확인하고, 실패는 404로 숨긴다. */
    private User requireCurrentFriend(Long viewerId, Long friendUserId) {
        if (viewerId == null || friendUserId == null || viewerId.equals(friendUserId)) {
            throw new FriendDomainException(ErrorCode.USER_NOT_FOUND);
        }

        User friend = userRepository.findByUserIdAndDeletedAtIsNull(friendUserId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.USER_NOT_FOUND));
        UserPair pair = UserPair.of(viewerId, friendUserId);
        boolean areFriends = friendshipRepository.existsByUserLow_UserIdAndUserHigh_UserId(
                pair.lowId(), pair.highId());
        if (!areFriends) {
            throw new FriendDomainException(ErrorCode.USER_NOT_FOUND);
        }
        return friend;
    }
}
