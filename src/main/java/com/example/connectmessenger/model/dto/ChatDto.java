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
    /** Title of a group chat, null for 1:1 chats. */
    private final String name;
    private final boolean group;
    private final List<Long> adminIds;
    /** The next two are the requesting user's own settings. */
    private final boolean archived;
    private final boolean muted;
    /** Messages of other users newer than the last one the requesting user has seen. */
    private final long unreadCount;
}
