package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.CreateMessageRequest;
import com.example.connectmessenger.model.dto.MessageDto;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.service.AwsS3Client;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AwsS3Client awsS3Client;
    private final MessageService messageService;
    private final MessageRepo messageRepo;
    private final ChatService chatService;
    private final SimpMessagingTemplate template;

    @GetMapping("/message/{id}")
    public MessageDto getMessageById(@PathVariable long id, @AuthenticationPrincipal User principal) {
        Message message = messageService.getMessageById(id);
        chatService.assertMember(message.getChatId(), principal.getId());
        return MessageDto.from(message);
    }

    @PostMapping(value = "/message", consumes = MULTIPART_FORM_DATA_VALUE)
    public MessageDto postMessage(CreateMessageRequest message, @AuthenticationPrincipal User principal) {
        // userId is optional: the sender is always the authenticated user
        if (message.userId() != null && !principal.getId().equals(message.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot post as another user");
        }
        chatService.assertMember(message.chatId(), principal.getId());
        var request = new CreateMessageRequest(message.chatId(), principal.getId(), message.time(),
                message.type(), message.textContent(), message.fileContent());
        Message added = messageService.addMessage(request);
        var dto = MessageDto.from(added);
        template.convertAndSend("/topic/messages/" + added.getChatId(), dto);
        return dto;
    }

    /**
     * Pages of a chat's history, oldest first within the page; use the smallest id as `before` for the next one.
     * With `after` it returns the messages newer than that id instead (oldest first).
     */
    @GetMapping("/chats/chat/{chatId}/messages")
    public List<MessageDto> getMessages(@PathVariable long chatId,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Long after,
                                        @RequestParam(defaultValue = "50") int size,
                                        @AuthenticationPrincipal User principal) {
        chatService.assertMember(chatId, principal.getId());
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        if (after != null) {
            // catch-up after a reconnect: the messages following the last known one, oldest first
            return messageRepo.findByChatIdAndIdGreaterThanOrderByIdAsc(chatId, after, Limit.of(limit))
                    .stream().map(MessageDto::from).toList();
        }
        var page = new ArrayList<>(messageRepo.findByChatIdAndIdLessThanOrderByIdDesc(
                chatId, before == null ? Long.MAX_VALUE : before, Limit.of(limit)));
        Collections.reverse(page);
        return page.stream().map(MessageDto::from).toList();
    }

    @GetMapping("/message/file/{fileName}")
    public String getMessageImage(@PathVariable String fileName, @AuthenticationPrincipal User principal) {
        Message message = messageRepo.findByTypeAndContent(MessageType.FILE, fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        chatService.assertMember(message.getChatId(), principal.getId());
        return Base64.getEncoder().encodeToString(awsS3Client.getFile(fileName));
    }

    /** Same as the Base64 variant but streams the image bytes with their real content type. */
    @GetMapping("/message/file/{fileName}/raw")
    public ResponseEntity<byte[]> getMessageImageRaw(@PathVariable String fileName, @AuthenticationPrincipal User principal) {
        Message message = messageRepo.findByTypeAndContent(MessageType.FILE, fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        chatService.assertMember(message.getChatId(), principal.getId());
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.endsWith(".gif") ? MediaType.IMAGE_GIF : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(awsS3Client.getFile(fileName));
    }
}
