package com.ktb4.team16.mulo.user.controller;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.FieldError;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.exception.PasswordChangeException;
import com.ktb4.team16.mulo.user.service.SignupCommand;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    @Mock UserSignupService signupService;
    @Mock UserProfileService profileService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new UserController(signupService, profileService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSignupReturnsCreated() throws Exception {
        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"user@example.com\","
                                + "\"password\":\"Test1234!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));
    }

    @Test
    void missingSignupFieldsReturnEveryRequiredError() throws Exception {
        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "NICKNAME_REQUIRED", "EMAIL_REQUIRED", "PASSWORD_REQUIRED")));
    }

    @Test
    void invalidSignupFormatsReturnEveryFormatError() throws Exception {
        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"bad name\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "INVALID_NICKNAME_FORMAT", "INVALID_EMAIL_FORMAT", "INVALID_PASSWORD_FORMAT")));
    }

    @Test
    void emailLongerThanDatabaseLimitReturnsInvalidEmailFormat() throws Exception {
        String email = "a".repeat(245) + "@example.com";

        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"" + email
                                + "\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code")
                        .value("INVALID_EMAIL_FORMAT"));
    }

    @Test
    void duplicateSignupReturnsConflictWithEveryDuplicateField() throws Exception {
        doThrow(new DuplicateUserException(List.of(
                FieldError.of("email", ErrorCode.EMAIL_DUPLICATED),
                FieldError.of("nickname", ErrorCode.NICKNAME_DUPLICATED)
        ))).when(signupService).signup(new SignupCommand(
                "뮤로16", "used@example.com", "Test1234!"));

        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"used@example.com\","
                                + "\"password\":\"Test1234!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "EMAIL_DUPLICATED", "NICKNAME_DUPLICATED")));
    }

    @Test
    void unexpectedSignupFailureReturnsCommonInternalError() throws Exception {
        doThrow(new IllegalStateException()).when(signupService)
                .signup(new SignupCommand(
                        "뮤로16", "user@example.com", "Test1234!"));

        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"user@example.com\","
                                + "\"password\":\"Test1234!\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void authenticatedUserCanGetOwnProfile() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                "회원 정보 조회 성공",
                new UserProfileResponse.UserProfileData(
                        1L, "뮤로16", "user@example.com"));
        when(profileService.getMyProfile(1L)).thenReturn(response);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));

        mvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 정보 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("뮤로16"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void authenticatedUserCanUpdateNickname() throws Exception {
        when(profileService.updateNickname(1L, "새닉네임"))
                .thenReturn(new UpdateNicknameResponse(
                        "닉네임이 변경되었습니다.",
                        new UpdateNicknameResponse.UpdateNicknameData("새닉네임")));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("닉네임이 변경되었습니다."))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    void blankNicknameReturnsRequiredError() throws Exception {
        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_REQUIRED"));
    }

    @Test
    void unicodeBlankNicknameReturnsOnlyRequiredError() throws Exception {
        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\u3000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_REQUIRED"));
    }

    @Test
    void invalidNicknameReturnsFormatError() throws Exception {
        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"bad name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_NICKNAME_FORMAT"));
    }

    @Test
    void currentNicknameReturnsSameNicknameConflict() throws Exception {
        when(profileService.updateNickname(1L, "현재닉네임"))
                .thenThrow(new NicknameConflictException(ErrorCode.SAME_NICKNAME));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"현재닉네임\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_NICKNAME"))
                .andExpect(jsonPath("$.message").value("현재 닉네임과 동일합니다."));
    }

    @Test
    void duplicateNicknameReturnsNicknameDuplicatedConflict() throws Exception {
        when(profileService.updateNickname(1L, "사용중닉네임"))
                .thenThrow(new NicknameConflictException(ErrorCode.NICKNAME_DUPLICATED));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"사용중닉네임\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
    }

    @Test
    void authenticatedUserCanUpdatePassword() throws Exception {
        when(profileService.updatePassword(1L, "Test1234!", "New1234!"))
                .thenReturn(new UpdatePasswordResponse("비밀번호가 변경되었습니다."));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Test1234!\","
                                + "\"newPassword\":\"New1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));
    }

    @Test
    void missingPasswordFieldsReturnEveryRequiredError() throws Exception {
        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].code", hasItems(
                        "CURRENT_PASSWORD_REQUIRED", "NEW_PASSWORD_REQUIRED")));
    }

    @Test
    void invalidNewPasswordReturnsFormatError() throws Exception {
        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Test1234!\","
                                + "\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD_FORMAT"));
    }

    @Test
    void unicodeBlankNewPasswordReturnsOnlyRequiredError() throws Exception {
        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Test1234!\","
                                + "\"newPassword\":\"\u3000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("NEW_PASSWORD_REQUIRED"));
    }

    @Test
    void mismatchedCurrentPasswordReturnsBadRequest() throws Exception {
        when(profileService.updatePassword(1L, "Wrong1234!", "New1234!"))
                .thenThrow(new PasswordChangeException(
                        ErrorCode.CURRENT_PASSWORD_MISMATCH));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong1234!\","
                                + "\"newPassword\":\"New1234!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"))
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 일치하지 않습니다."));
    }

    @Test
    void sameNewPasswordReturnsConflict() throws Exception {
        when(profileService.updatePassword(1L, "Test1234!", "Test1234!"))
                .thenThrow(new PasswordChangeException(ErrorCode.SAME_PASSWORD));
        authenticateUser(1L);

        mvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Test1234!\","
                                + "\"newPassword\":\"Test1234!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"))
                .andExpect(jsonPath("$.message")
                        .value("새 비밀번호는 현재 비밀번호와 달라야 합니다."));
    }

    private void authenticateUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
    }
}
