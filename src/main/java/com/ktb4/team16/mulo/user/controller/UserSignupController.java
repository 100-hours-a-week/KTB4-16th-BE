package com.ktb4.team16.mulo.user.controller;

import com.ktb4.team16.mulo.user.dto.request.UserSignupRequest;
import com.ktb4.team16.mulo.user.dto.response.UserSignupResponse;
import com.ktb4.team16.mulo.user.message.UserMessage;
import com.ktb4.team16.mulo.user.service.SignupCommand;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserSignupController {
    private final UserSignupService userSignupService;

    public UserSignupController(UserSignupService userSignupService) {
        this.userSignupService = userSignupService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSignupResponse signup(@Valid @RequestBody UserSignupRequest request) {
        // HTTP 요청 DTO를 서비스 계층이 사용하는 명령 객체로 변환한다.
        userSignupService.signup(new SignupCommand(request.nickname(), request.email(), request.password()));
        return new UserSignupResponse(UserMessage.SIGNUP_COMPLETED.message());
    }
}
