package com.example.connectmessenger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;

import java.time.LocalDateTime;

/** What a user has seen in a chat: the last displayed message and when it was first displayed. */
public record StatusDto(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Id id, Long messageId, LocalDateTime time) {
    public record Id(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long chatId) {
    }

    public static StatusDto from(Status s) {
        var id = s.getId() == null ? null : new Id(s.getId().getUserId(), s.getId().getChatId());
        return new StatusDto(id, s.getMessageId(), s.getTime());
    }

    public Status toEntity() {
        var embeddedId = id == null ? null : new StatusEmbeddedId(id.userId(), id.chatId());
        return Status.builder().id(embeddedId).messageId(messageId).time(time).build();
    }
}
