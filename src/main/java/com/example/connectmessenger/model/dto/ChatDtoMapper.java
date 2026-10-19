package com.example.connectmessenger.model.dto;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatDtoMapper {
    private ChatDtoMapper() {}
    public static List<ChatDto> mapToChatDtos(List<Chat> conversations) {
        return conversations.stream()
                .map(ChatDtoMapper::mapToChatDto)
                .collect(Collectors.toList());
    }
    // copies lazy Hibernate collections while the transaction is open, so they can be serialized later
    private static <T> List<T> copyOf(List<T> list) {
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public static ChatDto mapToChatDto(Chat c) {
        return mapToChatDto(c, null);
    }

    /** `viewerId` selects whose personal settings (archived, muted) the DTO carries. */
    public static ChatDto mapToChatDto(Chat c, Long viewerId) {
        List<Status> statuses = copyOf(c.getStatus());
        Status own = viewerId == null ? null : statuses.stream()
                .filter(s -> s.getId() != null && viewerId.equals(s.getId().getUserId())).findFirst().orElse(null);
        return ChatDto.builder()
                .id(c.getId())
                .messages(copyOf(c.getMessages()).stream().map(MessageDto::from).toList())
                .users(UserDtoMapper.mapToUserDtos(c.getUsers()))
                .status(statuses.stream().map(StatusDto::from).toList())
                .name(c.getName())
                .group(c.isGroupChat())
                .adminIds(statuses.stream().filter(Status::isGroupAdmin).map(s -> s.getId().getUserId()).toList())
                .archived(own != null && own.isArchived())
                .muted(own != null && own.isMuted())
                .build();
    }
}
