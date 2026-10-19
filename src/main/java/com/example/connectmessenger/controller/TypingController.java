package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.TypingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class TypingController {
    private final SimpMessagingTemplate template;

    /** Ephemeral "is typing" signal; the interceptor has already checked the sender is a member of the chat. */
    @MessageMapping("/chats/{chatId}/typing")
    public void typing(@DestinationVariable Long chatId, Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            template.convertAndSend("/topic/typing/" + chatId, new TypingDto(chatId, user.getId()));
        }
    }
}
