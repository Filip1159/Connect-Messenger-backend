package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.ChatDto;
import com.example.connectmessenger.model.dto.ChatDtoMapper;
import com.example.connectmessenger.model.dto.CreateChatRequest;
import com.example.connectmessenger.model.dto.StatusDto;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.repo.UserBlockRepo;
import com.example.connectmessenger.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {
    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_MEMBERS = 50;

    /** `created` is false when a 1:1 chat of these two users already existed and was returned instead. */
    public record CreatedChat(ChatDto chat, boolean created) {
    }

    private final ChatRepo chatRepo;
    private final StatusRepo statusRepo;
    private final UserRepo userRepo;
    private final MessageRepo messageRepo;
    private final UserBlockRepo userBlockRepo;

    public void assertMember(Long chatId, Long userId) {
        if (chatId == null || userId == null || !chatRepo.existsByIdAndUsers_Id(chatId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this chat");
        }
    }

    public boolean sharesChat(Long userId, Long otherId) {
        return userId != null && otherId != null && (userId.equals(otherId) || chatRepo.sharesChat(userId, otherId));
    }

    /** Messages in a 1:1 chat stop when either of the two blocked the other; groups are not affected. */
    @Transactional(readOnly = true)
    public void assertCanPost(Long chatId, Long userId) {
        Chat chat = chatRepo.findById(chatId).orElse(null);
        if (chat == null || chat.isGroupChat()) {
            return;
        }
        chat.getUsers().stream().filter(u -> !u.getId().equals(userId)).findFirst().ifPresent(other -> {
            if (isBlockedEitherWay(userId, other.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't message this user");
            }
        });
    }

    @Transactional(readOnly = true)
    public ChatDto getChatById(Long id, Long viewerId) {
        return withUnreadCount(ChatDtoMapper.mapToChatDto(requireChat(id), viewerId), viewerId);
    }

    @Transactional(readOnly = true)
    public List<ChatDto> getUserChats(Long userId) {
        List<Chat> chats = chatRepo.findByUsers_Id(userId);
        return chats.stream().map(c -> withUnreadCount(ChatDtoMapper.mapToChatDto(c, userId), userId)).toList();
    }

    /** The chat as every member sees it (their own unread count and settings), keyed by user id. */
    @Transactional(readOnly = true)
    public Map<Long, ChatDto> getChatForEachMember(Long chatId) {
        Chat chat = requireChat(chatId);
        Map<Long, ChatDto> result = new LinkedHashMap<>();
        for (User member : chat.getUsers()) {
            result.put(member.getId(), withUnreadCount(ChatDtoMapper.mapToChatDto(chat, member.getId()), member.getId()));
        }
        return result;
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
    public CreatedChat createChat(CreateChatRequest request, Long creatorId) {
        if (request.userIds() == null || request.userIds().size() < 2
                || request.userIds().stream().distinct().count() != request.userIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat needs at least 2 distinct users");
        }
        if (request.userIds().size() > MAX_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat can have at most " + MAX_MEMBERS + " users");
        }
        String name = normalizeName(request.name(), false);
        boolean group = name != null || request.userIds().size() > 2;

        var users = request.userIds().stream()
                .map(id -> userRepo.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id)))
                .toList();
        for (User user : users) {
            if (!user.getId().equals(creatorId) && isBlockedEitherWay(creatorId, user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't start a chat with this user");
            }
        }

        if (!group) {
            Long otherId = request.userIds().stream().filter(id -> !id.equals(creatorId)).findFirst().orElseThrow();
            var existing = chatRepo.findDirectChats(creatorId, otherId);
            if (!existing.isEmpty()) {
                return new CreatedChat(reopened(existing.getFirst(), creatorId), false);
            }
        }

        var newChat = Chat.builder()
                .users(users)
                .name(name)
                .groupChat(group)
                .messages(new ArrayList<>())
                .status(new ArrayList<>())
                .build();
        var savedChat = chatRepo.save(newChat);

        var newStatuses = users.stream()
                .map(user -> Status.builder()
                        .messageId(null)
                        .time(null)
                        .groupAdmin(group && user.getId().equals(creatorId))
                        .id(StatusEmbeddedId.builder()
                                .userId(user.getId())
                                .chatId(savedChat.getId())
                                .build())
                        .build())
                .toList();

        // saveAll merges (the ids are assigned), so attach the returned managed instances, not the originals
        savedChat.setStatus(statusRepo.saveAll(newStatuses));

        return new CreatedChat(ChatDtoMapper.mapToChatDto(savedChat, creatorId), true);
    }

    /** Starting a chat that is archived brings it back to the list. */
    private ChatDto reopened(Chat chat, Long userId) {
        Status own = membership(chat, userId);
        if (own.isArchived()) {
            own.setArchived(false);
            statusRepo.save(own);
        }
        return withUnreadCount(ChatDtoMapper.mapToChatDto(chat, userId), userId);
    }

    /** Archive / mute for the requesting user only; null leaves a flag unchanged. */
    @Transactional
    public void updateSettings(Long chatId, Long userId, Boolean archived, Boolean muted) {
        Status own = membership(requireChat(chatId), userId);
        if (archived != null) own.setArchived(archived);
        if (muted != null) own.setMuted(muted);
        statusRepo.save(own);
    }

    @Transactional
    public void rename(Long chatId, Long actorId, String name) {
        Chat chat = requireGroup(chatId);
        requireAdmin(chat, actorId);
        chat.setName(normalizeName(name, true));
        chatRepo.save(chat);
    }

    @Transactional
    public void addMembers(Long chatId, Long actorId, List<Long> userIds) {
        Chat chat = requireGroup(chatId);
        requireAdmin(chat, actorId);
        if (userIds == null || userIds.isEmpty() || userIds.stream().distinct().count() != userIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userIds must have at least one distinct user");
        }
        var memberIds = chat.getStatus().stream().map(s -> s.getId().getUserId()).toList();
        if (memberIds.size() + userIds.size() > MAX_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat can have at most " + MAX_MEMBERS + " users");
        }
        // a new member starts reading from now on: the history stays readable but is not "unread"
        Long lastMessageId = messageRepo.findFirstByChatIdOrderByIdDesc(chatId).map(m -> m.getId()).orElse(null);
        var added = new ArrayList<Status>();
        for (Long id : userIds) {
            if (memberIds.contains(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User " + id + " is already a member");
            }
            userRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
            if (isBlockedEitherWay(actorId, id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't add this user");
            }
            added.add(Status.builder().id(new StatusEmbeddedId(id, chatId)).messageId(lastMessageId).build());
        }
        statusRepo.saveAll(added);
    }

    /** An admin removes a member, or a member leaves (target = actor). Returns the user who is out. */
    @Transactional
    public User removeMember(Long chatId, Long actorId, Long targetId) {
        Chat chat = requireGroup(chatId);
        Status actor = membership(chat, actorId);
        if (!actorId.equals(targetId) && !actor.isGroupAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a group admin can remove members");
        }
        Status target = chat.getStatus().stream().filter(s -> targetId.equals(s.getId().getUserId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this chat"));
        User user = chat.getUsers().stream().filter(u -> u.getId().equals(targetId)).findFirst().orElseThrow();
        chat.getStatus().remove(target);  // the loaded chat must not keep pointing at the deleted row
        statusRepo.delete(target);

        // a group never stays without an admin
        var remaining = chat.getStatus();
        if (target.isGroupAdmin() && remaining.stream().noneMatch(Status::isGroupAdmin)) {
            remaining.stream().min(Comparator.comparing(s -> s.getId().getUserId())).ifPresent(next -> {
                next.setGroupAdmin(true);
                statusRepo.save(next);
            });
        }
        return user;
    }

    @Transactional
    public void setAdmin(Long chatId, Long actorId, Long targetId, boolean admin) {
        Chat chat = requireGroup(chatId);
        requireAdmin(chat, actorId);
        Status target = chat.getStatus().stream().filter(s -> targetId.equals(s.getId().getUserId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a member of this chat"));
        if (!admin && target.isGroupAdmin()
                && chat.getStatus().stream().filter(Status::isGroupAdmin).count() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A group needs at least one admin");
        }
        target.setGroupAdmin(admin);
        statusRepo.save(target);
    }

    private Chat requireChat(Long id) {
        return chatRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private Chat requireGroup(Long chatId) {
        Chat chat = requireChat(chatId);
        if (!chat.isGroupChat()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This is not a group chat");
        }
        return chat;
    }

    private static Status membership(Chat chat, Long userId) {
        return chat.getStatus().stream().filter(s -> s.getId() != null && Objects.equals(userId, s.getId().getUserId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this chat"));
    }

    private static void requireAdmin(Chat chat, Long userId) {
        if (!membership(chat, userId).isGroupAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a group admin can do this");
        }
    }

    /** Trims the name; blank is "no name" unless it is required. */
    private static String normalizeName(String name, boolean required) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name can't be empty");
            }
            return null;
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name can have at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private boolean isBlockedEitherWay(Long a, Long b) {
        return userBlockRepo.findByBlockerIdAndBlockedId(a, b).isPresent()
                || userBlockRepo.findByBlockerIdAndBlockedId(b, a).isPresent();
    }
}
