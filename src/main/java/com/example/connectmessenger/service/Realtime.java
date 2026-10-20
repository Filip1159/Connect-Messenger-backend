package com.example.connectmessenger.service;

import com.example.connectmessenger.model.dto.Event;

/**
 * What the app pushes to connected clients (Socket.IO, see SocketIoRealtime). A client follows "rooms"; the room of
 * a chat's messages is {@link Rooms#messages(Long)} and so on. Sending to a room nobody follows does nothing.
 */
public interface Realtime {
    /** Sends the event to everybody following the room. */
    void publish(String room, Event<?> event);

    /**
     * The user is no longer in the chat: stop what they follow of it. A room is only checked when it is joined, so
     * without this the user would keep receiving the chat's messages.
     */
    void leaveChat(Long userId, Long chatId);

    /** A connection that was accepted (its token was valid) or ended; PresenceService listens to these. */
    record SessionOpened(String sessionId, Long userId) {
    }

    record SessionClosed(String sessionId) {
    }
}
