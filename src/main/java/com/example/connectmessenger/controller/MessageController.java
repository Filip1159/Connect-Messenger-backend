package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.CreateMessageRequest;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.service.AwsS3Client;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final AwsS3Client awsS3Client;
    private final MessageService messageService;
    private final MessageRepo messageRepo;
    private final ChatService chatService;
    private final SimpMessagingTemplate template;

    @GetMapping("/message/{id}")
    public Message getMessageById(@PathVariable long id, @AuthenticationPrincipal User principal) {
        Message message = messageService.getMessageById(id);
        chatService.assertMember(message.getChatId(), principal.getId());
        return message;
    }

    @PostMapping(value = "/message", consumes = MULTIPART_FORM_DATA_VALUE)
    public Message postMessage(CreateMessageRequest message, @AuthenticationPrincipal User principal) {
        if (!principal.getId().equals(message.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot post as another user");
        }
        chatService.assertMember(message.chatId(), principal.getId());
        Message added = messageService.addMessage(message);
        template.convertAndSend("/topic/messages/" + added.getChatId(), added);
        return added;
    }

    @GetMapping("/message/file/{fileName}")
    String getMessageImage(@PathVariable String fileName, @AuthenticationPrincipal User principal) {
        Message message = messageRepo.findByTypeAndContent(MessageType.FILE, fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        chatService.assertMember(message.getChatId(), principal.getId());
        return Base64.getEncoder().encodeToString(awsS3Client.getFile(fileName));
    }
}
