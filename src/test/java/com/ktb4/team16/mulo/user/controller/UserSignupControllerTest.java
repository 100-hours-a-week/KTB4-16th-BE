package com.ktb4.team16.mulo.user.controller;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.FieldError;
import com.ktb4.team16.mulo.user.exception.DuplicateUserException;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;
import static org.hamcrest.Matchers.hasItems;

@ExtendWith(MockitoExtension.class)
class UserSignupControllerTest {
    @Mock UserSignupService signupService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserSignupController(signupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validSignupReturnsCreated() throws Exception {
        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"user@example.com\",\"password\":\"Test1234!\"}"))
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
        ))).when(signupService).signup(new com.ktb4.team16.mulo.user.service.SignupCommand(
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
                .signup(new com.ktb4.team16.mulo.user.service.SignupCommand(
                        "뮤로16", "user@example.com", "Test1234!"));

        mvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"뮤로16\",\"email\":\"user@example.com\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
