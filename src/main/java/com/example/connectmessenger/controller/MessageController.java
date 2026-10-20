package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.CreateMessageRequest;
import com.example.connectmessenger.model.dto.Event;
import com.example.connectmessenger.model.dto.MessageDto;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.service.AwsS3Client;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.MessageService;
import com.example.connectmessenger.service.ReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.example.connectmessenger.service.Realtime;
import com.example.connectmessenger.service.Rooms;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    public record EditMessageRequest(String textContent) {
    }

    public record ReactionRequest(String emoji) {
    }

    private final AwsS3Client awsS3Client;
    private final MessageService messageService;
    private final MessageRepo messageRepo;
    private final ChatService chatService;
    private final ReactionService reactionService;
    private final Realtime realtime;

    @GetMapping("/api/v1/messages/{id}")
    public MessageDto getMessageById(@PathVariable long id, @AuthenticationPrincipal User principal) {
        Message message = messageService.getMessageById(id);
        chatService.assertMember(message.getChatId(), principal.getId());
        return reactionService.toDto(message);
    }

    @PostMapping(value = "/api/v1/messages", consumes = MULTIPART_FORM_DATA_VALUE)
    public MessageDto postMessage(CreateMessageRequest message, @AuthenticationPrincipal User principal) {
        // userId is optional: the sender is always the authenticated user
        if (message.userId() != null && !principal.getId().equals(message.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot post as another user");
        }
        chatService.assertMember(message.chatId(), principal.getId());
        chatService.assertCanPost(message.chatId(), principal.getId());
        var request = new CreateMessageRequest(message.chatId(), principal.getId(), message.time(),
                message.type(), message.textContent(), message.fileContent(), message.replyToId());
        Message added = messageService.addMessage(request);
        return publish(Event.MESSAGE_CREATED, added);
    }

    @PatchMapping("/api/v1/messages/{id}")
    public MessageDto editMessage(@PathVariable long id, @RequestBody EditMessageRequest request,
                                  @AuthenticationPrincipal User principal) {
        return publish(Event.MESSAGE_EDITED, messageService.editMessage(id, principal.getId(), request.textContent()));
    }

    @DeleteMapping("/api/v1/messages/{id}")
    public MessageDto deleteMessage(@PathVariable long id, @AuthenticationPrincipal User principal) {
        return publish(Event.MESSAGE_DELETED, messageService.deleteMessage(id, principal.getId()));
    }

    @PutMapping("/api/v1/messages/{id}/reaction")
    public MessageDto react(@PathVariable long id, @RequestBody ReactionRequest request,
                            @AuthenticationPrincipal User principal) {
        Message message = messageService.getMessageById(id);
        chatService.assertMember(message.getChatId(), principal.getId());
        reactionService.react(message, principal.getId(), request.emoji());
        return publish(Event.MESSAGE_REACTED, message);
    }

    @DeleteMapping("/api/v1/messages/{id}/reaction")
    public MessageDto removeReaction(@PathVariable long id, @AuthenticationPrincipal User principal) {
        Message message = messageService.getMessageById(id);
        chatService.assertMember(message.getChatId(), principal.getId());
        reactionService.remove(message, principal.getId());
        return publish(Event.MESSAGE_REACTED, message);
    }

    /** Sends the current state of the message (with its reactions) to the chat and returns it. */
    private MessageDto publish(String eventType, Message message) {
        var dto = reactionService.toDto(message);
        realtime.publish(Rooms.messages(message.getChatId()), Event.of(eventType, dto));
        return dto;
    }

    /**
     * Pages of a chat's history, oldest first within the page; use the smallest id as `before` for the next one.
     * With `after` it returns the messages newer than that id instead (oldest first).
     * With `query` it searches the text messages instead: matches newest first, `before` pages through them.
     */
    @GetMapping("/api/v1/chats/{chatId}/messages")
    public List<MessageDto> getMessages(@PathVariable long chatId,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) Long after,
                                        @RequestParam(required = false) String query,
                                        @RequestParam(defaultValue = "50") int size,
                                        @AuthenticationPrincipal User principal) {
        chatService.assertMember(chatId, principal.getId());
        int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
        if (query != null) {
            String text = query.strip();
            if (text.isEmpty() || text.length() > MAX_QUERY_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "query must have 1 to " + MAX_QUERY_LENGTH + " characters");
            }
            return reactionService.toDtos(messageRepo
                    .findByChatIdAndTypeAndDeletedAtIsNullAndContentContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                            chatId, MessageType.TEXT, text, before == null ? Long.MAX_VALUE : before, Limit.of(limit)));
        }
        if (after != null) {
            // catch-up after a reconnect: the messages following the last known one, oldest first
            return reactionService.toDtos(
                    messageRepo.findByChatIdAndIdGreaterThanOrderByIdAsc(chatId, after, Limit.of(limit)));
        }
        var page = new ArrayList<>(messageRepo.findByChatIdAndIdLessThanOrderByIdDesc(
                chatId, before == null ? Long.MAX_VALUE : before, Limit.of(limit)));
        Collections.reverse(page);
        return reactionService.toDtos(page);
    }

    /** Streams the image bytes with their real content type. */
    @GetMapping("/api/v1/messages/files/{fileName}")
    public ResponseEntity<byte[]> getMessageImageRaw(@PathVariable String fileName, @AuthenticationPrincipal User principal) {
        Message message = messageRepo.findByTypeAndContent(MessageType.FILE, fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        chatService.assertMember(message.getChatId(), principal.getId());
        assertNotDeleted(message);
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.endsWith(".gif") ? MediaType.IMAGE_GIF : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(awsS3Client.getFile(fileName));
    }

    private static void assertNotDeleted(Message message) {
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
    }
}
