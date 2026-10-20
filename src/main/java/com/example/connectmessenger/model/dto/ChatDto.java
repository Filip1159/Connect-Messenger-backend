package com.example.connectmessenger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class ChatDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<MessageDto> messages;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<UserDto> users;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<StatusDto> status;
    /** Title of a group chat, null for 1:1 chats. */
    private final String name;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean group;
    private final List<Long> adminIds;
    /** The next two are the requesting user's own settings. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean archived;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean muted;
    /** Messages of other users newer than the last one the requesting user has seen. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final long unreadCount;
}
