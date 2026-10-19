package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.PresenceDto;
import com.example.connectmessenger.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PresenceController {
    private final PresenceService presenceService;

    /** Current presence of the people you share a chat with; changes arrive on /topic/presence/{userId}. */
    @GetMapping("/presence")
    public List<PresenceDto> getPresence(@AuthenticationPrincipal User principal) {
        return presenceService.presenceOfContacts(principal.getId());
    }
}
