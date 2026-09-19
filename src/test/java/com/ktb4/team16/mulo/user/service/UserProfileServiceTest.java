package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock UserRepository userRepository;
    UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository);
    }

    @Test
    void getMyProfileRejectsMissingActiveUser() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(1L))
                .isInstanceOf(UnauthenticatedUserException.class);
    }

    @Test
    void getMyProfileReturnsCurrentUserData() {
        User user = User.signup("user@example.com", "password-hash", "뮤로16");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(user));

        UserProfileResponse response = service.getMyProfile(1L);

        assertThat(response.message()).isEqualTo("회원 정보 조회 성공");
        assertThat(response.data().userId()).isEqualTo(1L);
        assertThat(response.data().nickname()).isEqualTo("뮤로16");
        assertThat(response.data().email()).isEqualTo("user@example.com");
    }
}
