package com.ktb4.team16.mulo.match.controller;

import com.ktb4.team16.mulo.match.dto.request.GroupChatJoinRequest;
import com.ktb4.team16.mulo.match.dto.response.GroupChatAvailabilityResponse;
import com.ktb4.team16.mulo.match.dto.response.GroupChatJoinResponse;
import com.ktb4.team16.mulo.match.service.GroupChatEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-chat-rooms")
public class GroupChatController {

    private final GroupChatEntryService groupChatEntryService;

    public GroupChatController(GroupChatEntryService groupChatEntryService) {
        this.groupChatEntryService = groupChatEntryService;
    }

    @GetMapping("/availability")
    public GroupChatAvailabilityResponse getAvailability() {
        return groupChatEntryService.getAvailability();
    }

    @PostMapping("/join")
    public ResponseEntity<GroupChatJoinResponse> join(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GroupChatJoinRequest request
    ) {
        GroupChatJoinResponse response = groupChatEntryService.join(userId, request);
        return ResponseEntity.status(response.data().newlyAssigned() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response);
    }
}
