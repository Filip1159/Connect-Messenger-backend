package com.example.connectmessenger.model.dto;

import com.example.connectmessenger.model.Chat;

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
        return ChatDto.builder()
                .id(c.getId())
                .messages(copyOf(c.getMessages()).stream().map(MessageDto::from).toList())
                .users(UserDtoMapper.mapToUserDtos(c.getUsers()))
                .status(copyOf(c.getStatus()).stream().map(StatusDto::from).toList())
                .build();
    }
}
