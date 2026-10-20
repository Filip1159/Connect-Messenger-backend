package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tells the members of a chat that the chat itself changed (created, renamed, members changed). */
@Component
@RequiredArgsConstructor
public class ChatNotifier {
    /** Sent to a user who is no longer in the chat. */
    public record Removed(Long chatId) {
    }

    private final ChatService chatService;
    private final Realtime realtime;

    /** Every current member receives the chat with their own unread count and settings. */
    public void chatChanged(Long chatId) {
        chatService.getChatForEachMember(chatId)
                .forEach((userId, chat) -> realtime.publish(Rooms.newChats(userId), Event.of(Event.CHAT_CHANGED, chat)));
    }

    /** The user is no longer in the chat: tell them, and stop what they already follow of it. */
    public void removed(Long chatId, User user) {
        realtime.publish(Rooms.removedChats(user.getId()), Event.of(Event.CHAT_REMOVED, new Removed(chatId)));
        realtime.leaveChat(user.getId(), chatId);
    }
}
