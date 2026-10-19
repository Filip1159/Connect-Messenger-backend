package com.example.connectmessenger.model.dto;

import java.time.LocalDateTime;

/** Whether a user has an open websocket session; `lastSeen` is when the last one closed (null if never). */
public record PresenceDto(Long userId, boolean online, LocalDateTime lastSeen) {
}
