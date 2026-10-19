package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class StatusController {

    private final StatusRepo statusRepo;
    private final MessageRepo messageRepo;
    private final ChatService chatService;
    private final SimpMessagingTemplate template;

    @PutMapping("/status")
    public void updateStatus(@RequestBody Status status, @AuthenticationPrincipal User principal) {
        if (status.getId() == null || !principal.getId().equals(status.getId().getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot update another user's status");
        }
        Long chatId = status.getId().getChatId();
        chatService.assertMember(chatId, principal.getId());

        if (status.getMessageId() != null) {
            Message displayed = messageRepo.findById(status.getMessageId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
            if (!chatId.equals(displayed.getChatId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this chat");
            }
        }

        statusRepo.save(status);
        template.convertAndSend("/topic/messages/" + chatId, status);
    }
}
