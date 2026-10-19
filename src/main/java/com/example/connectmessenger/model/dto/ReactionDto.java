package com.example.connectmessenger.model.dto;

import java.util.List;

/** Who reacted to a message with this emoji. */
public record ReactionDto(String emoji, List<Long> userIds) {
}
