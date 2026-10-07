package com.ktb4.team16.mulo.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class FriendCommandTransitionTest {

    private static final String SERVICE_CLASS =
            "com.ktb4.team16.mulo.friend.service.FriendCommandService";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Test
    void onlyTheAddresseeCanAcceptAndAcceptanceDeletesThePendingRequest() throws Exception {
        Object service = createService();
        User requester = user(12L);
        User addressee = user(35L);
        FriendRequest request = request(21L, requester, addressee);
        when(friendRequestRepository.findById(21L)).thenReturn(Optional.of(request));
        when(userRepository.lockActiveUsersByIdAscending(List.of(12L, 35L)))
                .thenReturn(List.of(requester, addressee));
        when(friendRequestRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(request));
        when(friendshipRepository.findByUserPairForUpdate(12L, 35L)).thenReturn(Optional.empty());
        when(friendshipRepository.save(Mockito.any(Friendship.class))).thenAnswer(invocation -> {
            Friendship saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "friendshipId", 31L);
            return saved;
        });

        Object result = invoke(service, "acceptFriendRequest",
                new Class<?>[]{Long.class, Long.class}, 35L, 21L);

        assertThat(result).isEqualTo(31L);
        verify(friendshipRepository).save(Mockito.any(Friendship.class));
        verify(friendRequestRepository).delete(request);
    }

    @Test
    void senderCannotAcceptTheirOwnRequest() throws Exception {
        Object service = createService();
        User requester = user(12L);
        User addressee = user(35L);
        FriendRequest request = request(21L, requester, addressee);
        when(friendRequestRepository.findById(21L)).thenReturn(Optional.of(request));
        when(userRepository.lockActiveUsersByIdAscending(List.of(12L, 35L)))
                .thenReturn(List.of(requester, addressee));
        when(friendRequestRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(request));

        assertDomainError(() -> invoke(service, "acceptFriendRequest",
                new Class<?>[]{Long.class, Long.class}, 12L, 21L),
                "FRIEND_REQUEST_NOT_FOUND");
        verify(friendshipRepository, never()).save(Mockito.any(Friendship.class));
        verify(friendRequestRepository, never()).delete(request);
    }

    @Test
    void eitherRequestParticipantCanDeleteButAThirdUserCannot() throws Exception {
        Object service = createService();
        User requester = user(12L);
        User addressee = user(35L);
        FriendRequest request = request(21L, requester, addressee);
        when(friendRequestRepository.findById(21L)).thenReturn(Optional.of(request));
        when(userRepository.lockActiveUsersByIdAscending(List.of(12L, 35L)))
                .thenReturn(List.of(requester, addressee));
        when(friendRequestRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(request), Optional.of(request));

        invoke(service, "deleteFriendRequest", new Class<?>[]{Long.class, Long.class}, 35L, 21L);
        assertDomainError(() -> invoke(service, "deleteFriendRequest",
                new Class<?>[]{Long.class, Long.class}, 99L, 21L),
                "FRIEND_REQUEST_NOT_FOUND");

        verify(friendRequestRepository).delete(request);
    }

    @Test
    void eitherFriendCanDeleteTheRelationshipButAThirdUserCannot() throws Exception {
        Object service = createService();
        User lower = user(12L);
        User higher = user(35L);
        Friendship friendship = Friendship.between(lower, higher);
        ReflectionTestUtils.setField(friendship, "friendshipId", 31L);
        when(friendshipRepository.findById(31L))
                .thenReturn(Optional.of(friendship), Optional.of(friendship), Optional.of(friendship));
        when(userRepository.lockActiveUsersByIdAscending(List.of(12L, 35L)))
                .thenReturn(List.of(lower, higher));
        when(friendshipRepository.findByIdForUpdate(31L))
                .thenReturn(Optional.of(friendship), Optional.of(friendship), Optional.of(friendship));

        invoke(service, "deleteFriendship", new Class<?>[]{Long.class, Long.class}, 12L, 31L);
        invoke(service, "deleteFriendship", new Class<?>[]{Long.class, Long.class}, 35L, 31L);
        assertDomainError(() -> invoke(service, "deleteFriendship",
                new Class<?>[]{Long.class, Long.class}, 99L, 31L), "FRIENDSHIP_NOT_FOUND");

        verify(friendshipRepository, Mockito.times(2)).delete(friendship);
    }

    /** 활성 요청 Entity의 low/high generated 값을 테스트에서 재현한다. */
    private static FriendRequest request(Long id, User requester, User addressee) {
        FriendRequest request = FriendRequest.pending(requester, addressee);
        ReflectionTestUtils.setField(request, "friendRequestId", id);
        ReflectionTestUtils.setField(request, "userLowId", 12L);
        ReflectionTestUtils.setField(request, "userHighId", 35L);
        return request;
    }

    /** 이미 발급된 사용자 ID를 가진 서비스용 User를 준비한다. */
    private static User user(Long id) {
        User user = User.signup("user" + id + "@example.com", "hash", "사용자" + id);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    /** 명령 서비스를 공개 생성자 계약으로 만든다. */
    private Object createService() throws Exception {
        Class<?> serviceType = Class.forName(SERVICE_CLASS);
        Constructor<?> constructor = serviceType.getConstructor(
                UserRepository.class, FriendRequestRepository.class, FriendshipRepository.class);
        return constructor.newInstance(userRepository, friendRequestRepository,
                friendshipRepository);
    }

    /** 전이 메서드를 호출하고 반환값을 검사한다. */
    private static Object invoke(Object service, String methodName, Class<?>[] types, Object... args)
            throws Exception {
        return service.getClass().getMethod(methodName, types).invoke(service, args);
    }

    /** 접근 거부 응답이 존재하지 않는 요청·관계와 같은 도메인 오류인지 확인한다. */
    private static void assertDomainError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String expectedCode
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(InvocationTargetException.class)
                .satisfies(error -> {
                    try {
                        Method errorCode = error.getCause().getClass().getMethod("errorCode");
                        assertThat(errorCode.invoke(error.getCause()).toString())
                                .isEqualTo(expectedCode);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError("FriendDomainException.errorCode() is required",
                                exception);
                    }
                });
    }
}
