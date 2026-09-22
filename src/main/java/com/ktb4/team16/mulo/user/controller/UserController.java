package com.ktb4.team16.mulo.user.controller;

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
import com.ktb4.team16.mulo.user.dto.request.UserSignupRequest;
import com.ktb4.team16.mulo.user.dto.response.UpdateNicknameResponse;
import com.ktb4.team16.mulo.user.dto.response.UpdatePasswordResponse;
import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.dto.response.UserSignupResponse;
import com.ktb4.team16.mulo.user.message.UserMessage;
import com.ktb4.team16.mulo.user.service.SignupCommand;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import com.ktb4.team16.mulo.user.service.UserSignupService;
import com.ktb4.team16.mulo.record.service.RecordService;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        MyPlaceRecordsResponseDto data =
                recordService.getMyPlaceRecords(
                        userId,
                        request.placeIds(),
                        request.cursor()
                );

        return new MyPlaceRecordsSearchResponse(
                RecordMessage.MY_PLACE_RECORDS_RETRIEVED.message(),
                data
        );
    }
}
