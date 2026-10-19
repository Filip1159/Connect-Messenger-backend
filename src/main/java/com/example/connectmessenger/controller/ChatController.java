package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.ChatDto;
import com.example.connectmessenger.model.dto.CreateChatRequest;
import com.example.connectmessenger.service.ChatNotifier;
import com.example.connectmessenger.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {
    public record RenameRequest(String name) {
    }

    public record SettingsRequest(Boolean archived, Boolean muted) {
    }

    public record AddMembersRequest(List<Long> userIds) {
    }

    private final ChatService chatService;
    private final ChatNotifier notifier;

    /** The chats of the signed-in user. */
    @GetMapping("/api/v1/chats")
    public List<ChatDto> getMyChats(@RequestParam(defaultValue = "true") boolean includeMessages,
                                    @AuthenticationPrincipal User principal) {
        return chatsOf(principal.getId(), includeMessages);
    }

    private List<ChatDto> chatsOf(Long userId, boolean includeMessages) {
        var chats = chatService.getUserChats(userId);
        if (includeMessages) return chats;
        // history is loaded page by page from /api/v1/chats/{id}/messages
        return chats.stream().map(c -> c.toBuilder().messages(List.of()).build()).toList();
    }

    @GetMapping("/api/v1/chats/{id}")
    public ChatDto getChatById(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        return chatService.getChatById(id, principal.getId());
    }

    /** Creates a chat; asking for a 1:1 chat that already exists returns it instead of making another. */
    @PostMapping("/api/v1/chats")
    public ChatDto create(@RequestBody CreateChatRequest request, @AuthenticationPrincipal User principal) {
        if (request.userIds() == null || !request.userIds().contains(principal.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create a chat you are not part of");
        }
        var result = chatService.createChat(request, principal.getId());
        if (result.created()) {
            notifier.chatChanged(result.chat().getId());
        }
        return result.chat();
    }

    /** Archive / mute: personal, nobody else is told. */
    @PatchMapping("/api/v1/chats/{id}/settings")
    public ChatDto updateSettings(@PathVariable Long id, @RequestBody SettingsRequest request,
                                  @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        chatService.updateSettings(id, principal.getId(), request.archived(), request.muted());
        return chatService.getChatById(id, principal.getId());
    }

    @PatchMapping("/api/v1/chats/{id}")
    public ChatDto rename(@PathVariable Long id, @RequestBody RenameRequest request,
                          @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        chatService.rename(id, principal.getId(), request.name());
        notifier.chatChanged(id);
        return chatService.getChatById(id, principal.getId());
    }

    @PostMapping("/api/v1/chats/{id}/members")
    public ChatDto addMembers(@PathVariable Long id, @RequestBody AddMembersRequest request,
                              @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        chatService.addMembers(id, principal.getId(), request.userIds());
        notifier.chatChanged(id);
        return chatService.getChatById(id, principal.getId());
    }

    /** An admin removes a member; removing yourself is leaving the group. */
    @DeleteMapping("/api/v1/chats/{id}/members/{userId}")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal User principal) {
        chatService.assertMember(id, principal.getId());
        User removed = chatService.removeMember(id, principal.getId(), userId);
        notifier.removed(id, removed);
        notifier.chatChanged(id);
    }

    @PutMapping("/api/v1/chats/{id}/admins/{userId}")
    public ChatDto makeAdmin(@PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal User principal) {
        return changeAdmin(id, userId, true, principal);
    }

    @DeleteMapping("/api/v1/chats/{id}/admins/{userId}")
    public ChatDto removeAdmin(@PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal User principal) {
        return changeAdmin(id, userId, false, principal);
    }

    private ChatDto changeAdmin(Long id, Long userId, boolean admin, User principal) {
        chatService.assertMember(id, principal.getId());
        chatService.setAdmin(id, principal.getId(), userId, admin);
        notifier.chatChanged(id);
        return chatService.getChatById(id, principal.getId());
    }
}
