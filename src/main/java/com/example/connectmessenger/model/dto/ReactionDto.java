package com.example.connectmessenger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Who reacted to a message with this emoji. */
public record ReactionDto(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String emoji, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> userIds) {
}
