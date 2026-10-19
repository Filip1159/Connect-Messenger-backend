package com.example.connectmessenger.model.dto;

/** Sent to a chat's typing topic while a member is writing. */
public record TypingDto(Long chatId, Long userId) {
}
