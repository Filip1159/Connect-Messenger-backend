package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;
import com.example.connectmessenger.model.dto.ChatDto;
import com.example.connectmessenger.model.dto.ChatDtoMapper;
import com.example.connectmessenger.model.dto.CreateChatRequest;
import com.example.connectmessenger.model.dto.StatusDto;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepo chatRepo;
    private final StatusRepo statusRepo;
    private final UserRepo userRepo;
    private final MessageRepo messageRepo;

    public void assertMember(Long chatId, Long userId) {
        if (chatId == null || userId == null || !chatRepo.existsByIdAndUsers_Id(chatId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this chat");
        }
    }

    public boolean sharesChat(Long userId, Long otherId) {
        return userId != null && otherId != null && (userId.equals(otherId) || chatRepo.sharesChat(userId, otherId));
    }

    @Transactional(readOnly = true)
    public ChatDto getChatById(Long id) {
        Chat c = chatRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
        return ChatDtoMapper.mapToChatDto(c);
    }

    @Transactional(readOnly = true)
    public List<ChatDto> getUserChats(Long userId) {
        List<Chat> chats = chatRepo.findByUsers_Id(userId);
        return ChatDtoMapper.mapToChatDtos(chats).stream().map(c -> withUnreadCount(c, userId)).toList();
    }

    private ChatDto withUnreadCount(ChatDto chat, Long userId) {
        long seen = chat.getStatus().stream()
                .filter(s -> s.id() != null && userId.equals(s.id().userId()) && s.messageId() != null)
                .mapToLong(StatusDto::messageId).findFirst().orElse(0L);
        return chat.toBuilder()
                .unreadCount(messageRepo.countByChatIdAndIdGreaterThanAndUserIdNot(chat.getId(), seen, userId))
                .build();
    }

    @Transactional
    public ChatDto createChat(CreateChatRequest request) {
        if (request.userIds() == null || request.userIds().size() < 2
                || request.userIds().stream().distinct().count() != request.userIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat needs at least 2 distinct users");
        }
        var users = request.userIds().stream()
                .map(id -> userRepo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id)))
                .toList();

        var newChat = Chat.builder()
                .users(users)
                .messages(new ArrayList<>())
                .status(new ArrayList<>())
                .build();
        var savedChat = chatRepo.save(newChat);

        var newStatuses = users.stream()
                .map(user -> Status.builder()
                        .messageId(null)
                        .time(null)
                        .id(StatusEmbeddedId.builder()
                                .userId(user.getId())
                                .chatId(savedChat.getId())
                                .build())
                        .build())
                .toList();

        // saveAll merges (the ids are assigned), so attach the returned managed instances, not the originals
        savedChat.setStatus(statusRepo.saveAll(newStatuses));

        return ChatDtoMapper.mapToChatDto(savedChat);
    }
}
