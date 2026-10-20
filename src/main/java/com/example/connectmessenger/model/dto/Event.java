package com.example.connectmessenger.model.dto;

/**
 * Everything the server pushes over the websocket is wrapped like this, so a client can tell what happened from
 * `type` instead of guessing from the fields of the payload. The destination (topic) still says who is told: it is
 * what the subscription checks are based on. Clients ignore types they do not know, so new ones can be added.
 */
public record Event<T>(String type, T payload) {
    /** /topic/messages/{chatId}: payload {@link MessageDto}. */
    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_EDITED = "message.edited";
    public static final String MESSAGE_DELETED = "message.deleted";
    /** A reaction was added, changed or removed; the payload is the whole message with its reactions. */
    public static final String MESSAGE_REACTED = "message.reacted";
    /** /topic/status/{chatId}: payload {@link StatusDto}. */
    public static final String STATUS_UPDATED = "status.updated";
    /** /topic/typing/{chatId}: payload {@link TypingDto}. */
    public static final String TYPING = "typing";
    /** /topic/presence/{userId}: payload {@link PresenceDto}. */
    public static final String PRESENCE_CHANGED = "presence.changed";
    /** /topic/new-chats/{userId}: a chat was created or changed (renamed, members); payload {@link ChatDto} for that user. */
    public static final String CHAT_CHANGED = "chat.changed";
    /** /topic/removed-chats/{userId}: the user is no longer in the chat; payload {chatId}. */
    public static final String CHAT_REMOVED = "chat.removed";

    public static <T> Event<T> of(String type, T payload) {
        return new Event<>(type, payload);
    }
}
