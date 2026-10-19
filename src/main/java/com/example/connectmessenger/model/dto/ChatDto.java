package com.example.connectmessenger.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class ChatDto {
    private final Long id;
    private final List<MessageDto> messages;
    private final List<UserDto> users;
    private final List<StatusDto> status;
    /** Messages of other users newer than the last one the requesting user has seen. */
    private final long unreadCount;
}
