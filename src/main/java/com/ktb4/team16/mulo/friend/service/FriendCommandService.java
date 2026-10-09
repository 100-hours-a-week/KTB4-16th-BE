package com.ktb4.team16.mulo.friend.service;

import com.ktb4.team16.mulo.friend.entity.FriendRequest;
import com.ktb4.team16.mulo.friend.entity.Friendship;
import com.ktb4.team16.mulo.friend.entity.UserPair;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.friend.repository.FriendRequestRepository;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 친구 쌍의 요청·수락·삭제 상태 변경과 권한을 담당한다. */
@Service
@RequiredArgsConstructor
public class FriendCommandService {

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

    /** 새 요청을 만들거나 상대의 pending 요청을 원자적으로 친구 관계로 전환한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SendFriendRequestResult sendFriendRequest(Long currentUserId, String nickname) {
        User requester = findActiveUser(currentUserId);
        User target = userRepository.findByNicknameAndDeletedAtIsNull(nickname)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.USER_NOT_FOUND));

        if (requester.getUserId().equals(target.getUserId())) {
            throw new FriendDomainException(ErrorCode.SELF_FRIEND_REQUEST_NOT_ALLOWED);
        }

        UserPair pair = UserPair.of(requester.getUserId(), target.getUserId());
        List<User> lockedUsers = lockActivePair(pair);
        requester = userForId(lockedUsers, currentUserId);
        target = userForId(lockedUsers, target.getUserId());

        Optional<Friendship> friendship = friendshipRepository.findByUserPairForUpdate(
                pair.lowId(), pair.highId());
        if (friendship.isPresent()) {
            throw new FriendDomainException(ErrorCode.FRIENDSHIP_ALREADY_EXISTS);
        }

        Optional<FriendRequest> pending = friendRequestRepository.findByUserPairForUpdate(
                pair.lowId(), pair.highId());
        if (pending.isPresent()) {
            FriendRequest existing = pending.get();
            if (existing.getRequester().getUserId().equals(currentUserId)) {
                throw new FriendDomainException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
            }

            friendRequestRepository.delete(existing);
            Friendship created = friendshipRepository.save(Friendship.between(requester, target));
            return new SendFriendRequestResult.BecameFriends(created.getFriendshipId());
        }

        FriendRequest created = friendRequestRepository.save(FriendRequest.pending(requester, target));
        return new SendFriendRequestResult.Pending(created.getFriendRequestId());
    }

    /** 수신자만 pending 요청을 친구 관계로 바꾸고 요청 행을 함께 제거한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long acceptFriendRequest(Long currentUserId, Long friendRequestId) {
        FriendRequest requestReference = friendRequestRepository.findById(friendRequestId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        UserPair pair = UserPair.of(requestReference.getUserLowId(), requestReference.getUserHighId());
        List<User> lockedUsers = lockActivePair(pair);
        FriendRequest request = friendRequestRepository.findByIdForUpdate(friendRequestId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!request.getAddressee().getUserId().equals(currentUserId)) {
            throw new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        Optional<Friendship> existing = friendshipRepository.findByUserPairForUpdate(
                pair.lowId(), pair.highId());
        if (existing.isPresent()) {
            throw new FriendDomainException(ErrorCode.FRIENDSHIP_ALREADY_EXISTS);
        }

        User requester = userForId(lockedUsers, request.getRequester().getUserId());
        User addressee = userForId(lockedUsers, request.getAddressee().getUserId());
        Friendship friendship = friendshipRepository.save(Friendship.between(requester, addressee));
        friendRequestRepository.delete(request);
        return friendship.getFriendshipId();
    }

    /** 요청 당사자만 기존 pending 요청을 거절하거나 취소할 수 있게 한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteFriendRequest(Long currentUserId, Long friendRequestId) {
        FriendRequest requestReference = friendRequestRepository.findById(friendRequestId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        UserPair pair = UserPair.of(requestReference.getUserLowId(), requestReference.getUserHighId());
        lockActivePair(pair);
        FriendRequest request = friendRequestRepository.findByIdForUpdate(friendRequestId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        if (!request.getRequester().getUserId().equals(currentUserId)
                && !request.getAddressee().getUserId().equals(currentUserId)) {
            throw new FriendDomainException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        friendRequestRepository.delete(request);
    }

    /** 친구 관계 당사자만 같은 쌍의 두 사용자 잠금을 얻은 뒤 관계를 제거한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteFriendship(Long currentUserId, Long friendshipId) {
        Friendship friendshipReference = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIENDSHIP_NOT_FOUND));
        UserPair pair = UserPair.of(friendshipReference.getUserLow().getUserId(),
                friendshipReference.getUserHigh().getUserId());
        lockActivePair(pair);
        Friendship friendship = friendshipRepository.findByIdForUpdate(friendshipId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.FRIENDSHIP_NOT_FOUND));

        if (!friendship.containsUser(currentUserId)) {
            throw new FriendDomainException(ErrorCode.FRIENDSHIP_NOT_FOUND);
        }

        friendshipRepository.delete(friendship);
    }

    /** 활성 사용자 두 행을 ID 오름차순으로 잠가 같은 쌍의 쓰기를 직렬화한다. */
    private List<User> lockActivePair(UserPair pair) {
        List<User> lockedUsers = userRepository.lockActiveUsersByIdAscending(
                List.of(pair.lowId(), pair.highId()));
        if (lockedUsers.size() != 2) {
            throw new FriendDomainException(ErrorCode.USER_NOT_FOUND);
        }
        return lockedUsers;
    }

    /** 요청자가 활성 상태인지 확인해 친구 명령에 사용할 Entity를 반환한다. */
    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new FriendDomainException(ErrorCode.USER_NOT_FOUND));
    }

    /** 쌍 잠금 결과에서 지정 사용자를 찾고 비활성 사용자 접근을 숨긴다. */
    private User userForId(List<User> users, Long userId) {
        return users.stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new FriendDomainException(ErrorCode.USER_NOT_FOUND));
    }
}
