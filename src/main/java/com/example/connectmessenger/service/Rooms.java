package com.example.connectmessenger.service;

/** Names of the rooms a client can follow. */
public final class Rooms {
    public static final String MESSAGES = "/topic/messages/";
    public static final String STATUS = "/topic/status/";
    public static final String TYPING = "/topic/typing/";
    public static final String PRESENCE = "/topic/presence/";
    public static final String NEW_CHATS = "/topic/new-chats/";
    public static final String REMOVED_CHATS = "/topic/removed-chats/";

    private Rooms() {
    }

    public static String messages(Long chatId) {
        return MESSAGES + chatId;
    }

    public static String status(Long chatId) {
        return STATUS + chatId;
    }

    public static String typing(Long chatId) {
        return TYPING + chatId;
    }

    public static String presence(Long userId) {
        return PRESENCE + userId;
    }

    public static String newChats(Long userId) {
        return NEW_CHATS + userId;
    }

    public static String removedChats(Long userId) {
        return REMOVED_CHATS + userId;
    }
}
