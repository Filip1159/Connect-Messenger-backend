package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.ChatDto;
import com.example.connectmessenger.model.dto.CreateChatRequest;
import com.example.connectmessenger.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final SimpMessagingTemplate template;

    @GetMapping("/{userId}")
    public List<ChatDto> getChatsByUserId(@PathVariable Long userId, @AuthenticationPrincipal User principal) {
        if (!principal.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot read another user's chats");
        }
        return chatService.getUserChats(userId);
    }

    @GetMapping("/chat/{id}")
    public ChatDto getChatById(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        return chatService.getChatById(id);
    }

    @PostMapping
    ChatDto create(@RequestBody CreateChatRequest request, @AuthenticationPrincipal User principal) {
        if (request.userIds() == null || !request.userIds().contains(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create a chat you are not part of");
        }
        var createdChat = chatService.createChat(request);
        request.userIds().forEach(id -> template.convertAndSend("/topic/new-chats/" + id, createdChat));
        return createdChat;
    }
}
