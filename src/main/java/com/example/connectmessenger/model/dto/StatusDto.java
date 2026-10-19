package com.example.connectmessenger.model.dto;

import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;

import java.time.LocalDateTime;

/** What a user has seen in a chat: the last displayed message and when it was first displayed. */
public record StatusDto(Id id, Long messageId, LocalDateTime time) {
    public record Id(Long userId, Long chatId) {
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
