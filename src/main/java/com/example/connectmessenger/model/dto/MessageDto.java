package com.example.connectmessenger.model.dto;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;

import java.time.LocalDateTime;

public record MessageDto(Long id, Long chatId, Long userId, LocalDateTime time, MessageType type, String content) {
    public static MessageDto from(Message m) {
        return new MessageDto(m.getId(), m.getChatId(), m.getUserId(), m.getTime(), m.getType(), m.getContent());
    }
}
