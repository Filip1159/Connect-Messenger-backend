package com.example.connectmessenger.model.dto;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * `editedAt` is set once the text was changed; `deleted` messages keep their id and time but have no content.
 * An edit, a delete or a reaction is published on the same topic as a new message, with the same id.
 * `replyToId` is the message this one answers (same chat), `reactions` are grouped by emoji.
 */
public record MessageDto(Long id, Long chatId, Long userId, LocalDateTime time, MessageType type, String content,
                         LocalDateTime editedAt, boolean deleted, Long replyToId, List<ReactionDto> reactions) {
    public static MessageDto from(Message m) {
        return from(m, List.of());
    }

    public static MessageDto from(Message m, List<ReactionDto> reactions) {
        return new MessageDto(m.getId(), m.getChatId(), m.getUserId(), m.getTime(), m.getType(),
                m.isDeleted() ? "" : m.getContent(), m.getEditedAt(), m.isDeleted(), m.getReplyToId(),
                m.isDeleted() ? List.of() : reactions);
    }
}
