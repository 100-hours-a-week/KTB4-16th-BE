package com.ktb4.team16.mulo.friend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FriendEntityTest {

    private static final String REQUEST_CLASS =
            "com.ktb4.team16.mulo.friend.entity.FriendRequest";
    private static final String FRIENDSHIP_CLASS =
            "com.ktb4.team16.mulo.friend.entity.Friendship";

    @Test
    void pendingRequestPreservesDirectionAndMapsGeneratedColumnsReadOnly() throws Exception {
        Class<?> requestType = Class.forName(REQUEST_CLASS);
        User requester = user(12L);
        User addressee = user(35L);
        Method factory = requestType.getMethod("pending", User.class, User.class);
        Object request = factory.invoke(null, requester, addressee);

        assertThat(field(requestType, "requester").get(request)).isSameAs(requester);
        assertThat(field(requestType, "addressee").get(request)).isSameAs(addressee);
        assertReadOnlyGeneratedColumn(requestType, "userLowId");
        assertReadOnlyGeneratedColumn(requestType, "userHighId");
        assertThat(requestType.getAnnotation(Table.class).name()).isEqualTo("friend_requests");
    }

    @Test
    void friendshipFactoryStoresUsersInAscendingIdOrderAndChecksMembership() throws Exception {
        Class<?> friendshipType = Class.forName(FRIENDSHIP_CLASS);
        User lowerIdUser = user(12L);
        User higherIdUser = user(35L);
        Object friendship = friendshipType
                .getMethod("between", User.class, User.class)
                .invoke(null, higherIdUser, lowerIdUser);

        assertThat(field(friendshipType, "userLow").get(friendship)).isSameAs(lowerIdUser);
        assertThat(field(friendshipType, "userHigh").get(friendship)).isSameAs(higherIdUser);
        assertThat(friendshipType.getMethod("containsUser", Long.class)
                .invoke(friendship, 12L)).isEqualTo(true);
        assertThat(friendshipType.getMethod("containsUser", Long.class)
                .invoke(friendship, 99L)).isEqualTo(false);
    }

    /** ID가 이미 발급된 사용자 상태를 도메인 팩토리에 전달한다. */
    private static User user(Long userId) {
        User user = User.signup("user" + userId + "@example.com", "hash", "닉네임" + userId);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    /** 테스트 중 비공개 필드 값으로 매핑된 관계와 컬럼 속성을 확인한다. */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** generated column은 INSERT와 UPDATE에서 제외되는지 확인한다. */
    private static void assertReadOnlyGeneratedColumn(Class<?> type, String fieldName)
            throws NoSuchFieldException {
        Column column = field(type, fieldName).getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.insertable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }
}
