package com.ktb4.team16.mulo.user.controller;

import com.ktb4.team16.mulo.global.config.SecurityProperties;
import com.ktb4.team16.mulo.place.dto.MyPlaceMarkerResponse;
import com.ktb4.team16.mulo.place.dto.MyPlacesQuery;
import com.ktb4.team16.mulo.place.dto.MyPlacesResponse;
import com.ktb4.team16.mulo.place.exception.InvalidMapBoundsException;
import com.ktb4.team16.mulo.place.message.PlaceMessage;
import com.ktb4.team16.mulo.place.service.PlaceService;
import com.ktb4.team16.mulo.record.dto.request.MyPlaceRecordsSearchRequest;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsResponseDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsSearchResponse;
import com.ktb4.team16.mulo.record.message.RecordMessage;
import com.ktb4.team16.mulo.user.dto.request.UpdateNicknameRequest;
import com.ktb4.team16.mulo.user.dto.request.UpdatePasswordRequest;
import com.ktb4.team16.mulo.user.dto.request.UpdatePreferredGenresRequest;
import com.ktb4.team16.mulo.user.dto.request.UserSignupRequest;
import com.ktb4.team16.mulo.user.dto.request.UserWithdrawalRequest;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePreferredGenresResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.dto.response.UserSignupResponse;
import com.ktb4.team16.mulo.user.dto.response.UserWithdrawalResponse;
import com.ktb4.team16.mulo.user.message.UserMessage;
import com.ktb4.team16.mulo.user.service.SignupCommand;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import com.ktb4.team16.mulo.user.service.UserWithdrawalService;
import com.ktb4.team16.mulo.record.service.RecordService;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserSignupService userSignupService;
    private final UserProfileService userProfileService;
    private final PlaceService placeService;
    private final RecordService recordService;
    private final UserWithdrawalService userWithdrawalService;
    private final SecurityProperties securityProperties;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSignupResponse signup(@Valid @RequestBody UserSignupRequest request) {
        // HTTP 요청 DTO를 서비스 계층이 사용하는 명령 객체로 변환한다.
        userSignupService.signup(
                new SignupCommand(request.nickname(), request.email(), request.password()));
        return new UserSignupResponse(UserMessage.SIGNUP_COMPLETED.message());
    }

    @GetMapping("/me")
    public UserProfileResponse getMyProfile(
            @AuthenticationPrincipal Long userId) {
        // 중요: Filter가 검증해 SecurityContext에 저장한 userId만 서비스에 전달한다.
        return userProfileService.getMyProfile(userId);
    }

    /** 비밀번호를 재확인해 탈퇴를 처리하고 현재 Refresh Cookie를 만료한다. */
    @DeleteMapping("/me")
    public ResponseEntity<UserWithdrawalResponse> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserWithdrawalRequest request) {
        userWithdrawalService.withdraw(userId, request.password());
        ResponseCookie expiredRefreshCookie = ResponseCookie.from("refreshToken", "")
                // 로그인 Cookie와 동일한 경로여야 브라우저가 기존 Cookie를 교체한다.
                .path("/api/auth")
                .httpOnly(true)
                .secure(securityProperties.cookieSecure())
                .sameSite("Strict")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
                .body(new UserWithdrawalResponse(UserMessage.WITHDRAWAL_COMPLETED.message()));
    }

    @PatchMapping("/me/nickname")
    public UpdateNicknameResponse updateNickname(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateNicknameRequest request) {
        return userProfileService.updateNickname(userId, request.nickname());
    }

    @PatchMapping("/me/password")
    public UpdatePasswordResponse updatePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdatePasswordRequest request) {
        return userProfileService.updatePassword(
                userId, request.currentPassword(), request.newPassword());
    }

    @PutMapping("/me/preferences/genres")
    public UpdatePreferredGenresResponse updatePreferredGenres(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdatePreferredGenresRequest request) {
        return userProfileService.updatePreferredGenres(userId, request.preferredGenres());
    }

    @GetMapping("/me/places")
    public MyPlacesResponse getMyPlaces(
            @AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute MyPlacesQuery query
    ) {
        if (!query.hasValidBounds()) {
            throw new InvalidMapBoundsException();
        }

        List<MyPlaceMarkerResponse> places =
                placeService.getMyPlaceMarkersInBounds(
                        userId, query.swLat(), query.swLng(), query.neLat(), query.neLng());

        return new MyPlacesResponse(
                PlaceMessage.MY_PLACES_RETRIEVED.message(),
                places
        );
    }

    @PostMapping("/me/records/search")
    public MyPlaceRecordsSearchResponse searchMyPlaceRecords(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MyPlaceRecordsSearchRequest request
    ) {
        MyPlaceRecordsResponseDto myPlaceRecords =
                recordService.getMyPlaceRecords(
                        userId,
                        request.placeIds(),
                        request.cursor()
                );

        return new MyPlaceRecordsSearchResponse(
                RecordMessage.MY_PLACE_RECORDS_RETRIEVED.message(),
                myPlaceRecords
        );
    }
}
