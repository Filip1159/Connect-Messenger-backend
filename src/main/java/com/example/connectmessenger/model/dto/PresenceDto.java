package com.example.connectmessenger.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Whether a user has an open websocket session; `lastSeen` is when the last one closed (null if never). */
public record PresenceDto(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long userId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean online, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDateTime lastSeen) {
}
