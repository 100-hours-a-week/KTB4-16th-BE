package com.ktb4.team16.mulo.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.friend.entity.FriendRequest;
import com.ktb4.team16.mulo.friend.entity.Friendship;
import com.ktb4.team16.mulo.friend.repository.FriendRequestRepository;
import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendCommandServiceTest {

    private static final String SERVICE_CLASS =
            "com.ktb4.team16.mulo.friend.service.FriendCommandService";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Test
    void createsPendingRequestAfterLockingBothUsersInAscendingIdOrder() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        User addressee = user(35L, "대상자");
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.of(addressee), "대상자");
        stub(userRepository, UserRepository.class, "lockActiveUsersByIdAscending",
                new Class<?>[]{List.class}, List.of(requester, addressee), List.of(12L, 35L));
        stub(friendshipRepository, FriendshipRepository.class,
                "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.empty(), 12L, 35L);
        stub(friendRequestRepository, FriendRequestRepository.class,
                "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.empty(), 12L, 35L);
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(invocation -> {
            FriendRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "friendRequestId", 21L);
            return saved;
        });

        Object result = send(service, 12L, "대상자");

        assertThat(result.getClass().getSimpleName()).isEqualTo("Pending");
        assertThat(result.getClass().getMethod("friendRequestId").invoke(result)).isEqualTo(21L);
        verify(friendRequestRepository).save(any(FriendRequest.class));
        verify(friendshipRepository, never()).save(any(Friendship.class));
        verifyLockOrder(List.of(12L, 35L));
    }

    @Test
    void turnsAnExistingReverseRequestIntoOneFriendship() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        User addressee = user(35L, "대상자");
        FriendRequest reverseRequest = FriendRequest.pending(addressee, requester);
        ReflectionTestUtils.setField(reverseRequest, "friendRequestId", 21L);
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.of(addressee), "대상자");
        stub(userRepository, UserRepository.class, "lockActiveUsersByIdAscending",
                new Class<?>[]{List.class}, List.of(requester, addressee), List.of(12L, 35L));
        stub(friendshipRepository, FriendshipRepository.class,
                "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.empty(), 12L, 35L);
        stub(friendRequestRepository, FriendRequestRepository.class,
                "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.of(reverseRequest), 12L, 35L);
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "friendshipId", 31L);
            return saved;
        });

        Object result = send(service, 12L, "대상자");

        assertThat(result.getClass().getSimpleName()).isEqualTo("BecameFriends");
        assertThat(result.getClass().getMethod("friendshipId").invoke(result)).isEqualTo(31L);
        verify(friendRequestRepository).delete(reverseRequest);
        verify(friendRequestRepository, never()).save(any(FriendRequest.class));
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void rejectsSendingARequestToTheSameUser() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.of(requester), "요청자");

        assertThatThrownBy(() -> send(service, 12L, "요청자"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .satisfies(error -> assertErrorCode(error.getCause(),
                        "SELF_FRIEND_REQUEST_NOT_ALLOWED"));
    }

    @Test
    void rejectsASecondRequestInTheSameDirection() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        User addressee = user(35L, "대상자");
        FriendRequest existing = FriendRequest.pending(requester, addressee);
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.of(addressee), "대상자");
        stub(userRepository, UserRepository.class, "lockActiveUsersByIdAscending",
                new Class<?>[]{List.class}, List.of(requester, addressee), List.of(12L, 35L));
        stub(friendshipRepository, FriendshipRepository.class, "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.empty(), 12L, 35L);
        stub(friendRequestRepository, FriendRequestRepository.class, "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.of(existing), 12L, 35L);

        assertThatThrownBy(() -> send(service, 12L, "대상자"))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> assertErrorCode(error.getCause(),
                        "FRIEND_REQUEST_ALREADY_EXISTS"));
    }

    @Test
    void rejectsARequestWhenTheUsersAreAlreadyFriends() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        User addressee = user(35L, "대상자");
        Friendship existing = Friendship.between(requester, addressee);
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.of(addressee), "대상자");
        stub(userRepository, UserRepository.class, "lockActiveUsersByIdAscending",
                new Class<?>[]{List.class}, List.of(requester, addressee), List.of(12L, 35L));
        stub(friendshipRepository, FriendshipRepository.class, "findByUserPairForUpdate",
                new Class<?>[]{Long.class, Long.class}, Optional.of(existing), 12L, 35L);

        assertThatThrownBy(() -> send(service, 12L, "대상자"))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> assertErrorCode(error.getCause(),
                        "FRIENDSHIP_ALREADY_EXISTS"));
    }

    @Test
    void returnsNotFoundForAnInactiveOrUnknownNickname() throws Exception {
        Object service = createService();
        User requester = user(12L, "요청자");
        stub(userRepository, UserRepository.class, "findByUserIdAndDeletedAtIsNull",
                new Class<?>[]{Long.class}, Optional.of(requester), 12L);
        stub(userRepository, UserRepository.class, "findByNicknameAndDeletedAtIsNull",
                new Class<?>[]{String.class}, Optional.empty(), "없는사용자");

        assertThatThrownBy(() -> send(service, 12L, "없는사용자"))
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> assertErrorCode(error.getCause(), "USER_NOT_FOUND"));
    }

    /** 공개 구현 전에도 서비스 생성과 인자 계약을 런타임에서 검증한다. */
    private Object createService() throws Exception {
        Class<?> serviceType = Class.forName(SERVICE_CLASS);
        Constructor<?> constructor = serviceType.getConstructor(
                UserRepository.class, FriendRequestRepository.class, FriendshipRepository.class);
        return constructor.newInstance(userRepository, friendRequestRepository,
                friendshipRepository);
    }

    /** 친구 요청 생성 동작을 서비스의 공개 명령 메서드로 호출한다. */
    private static Object send(Object service, Long requesterId, String nickname)
            throws Exception {
        return service.getClass().getMethod("sendFriendRequest", Long.class, String.class)
                .invoke(service, requesterId, nickname);
    }

    /** 각 저장소의 계약 메서드 응답을 Mockito mock에 지정한다. */
    private static void stub(
            Object repository,
            Class<?> repositoryType,
            String methodName,
            Class<?>[] parameterTypes,
            Object result,
            Object... arguments
    ) throws Exception {
        Method method = repositoryType.getMethod(methodName, parameterTypes);
        Mockito.when(method.invoke(repository, arguments)).thenReturn(result);
    }

    /** 서비스가 같은 사용자 쌍을 작은 ID부터 잠그도록 확인한다. */
    private void verifyLockOrder(List<Long> expectedIds) throws Exception {
        Method method = UserRepository.class.getMethod(
                "lockActiveUsersByIdAscending", List.class);
        method.invoke(Mockito.verify(userRepository), expectedIds);
    }

    /** 사용자와 친구 요청/관계 테스트용 ID를 준비한다. */
    private static User user(Long userId, String nickname) {
        User user = User.signup("user" + userId + "@example.com", "hash", nickname);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    /** 도메인 오류가 API 계약에 지정된 오류 코드로 분류되는지 확인한다. */
    private static void assertErrorCode(Throwable throwable, String expectedCode) {
        try {
            Method errorCode = throwable.getClass().getMethod("errorCode");
            assertThat(errorCode.invoke(throwable).toString()).isEqualTo(expectedCode);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("FriendDomainException.errorCode() is required", exception);
        }
    }
}
