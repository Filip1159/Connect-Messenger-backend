package com.example.connectmessenger.model.dto;

import java.util.List;

/** `name` makes a group chat (so does having more than two users); without it two users get a 1:1 chat. */
public record CreateChatRequest(
        List<Long> userIds,
        String name
) {
    public CreateChatRequest(List<Long> userIds) {
        this(userIds, null);
    }
}
