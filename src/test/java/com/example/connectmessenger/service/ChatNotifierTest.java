package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.ChatDto;
import com.example.connectmessenger.model.dto.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotifierTest {
    @Mock
    ChatService chatService;
    @Mock
    Realtime realtime;
    ChatNotifier notifier;
    User ewa;

    @BeforeEach
    void setUp() {
        notifier = new ChatNotifier(chatService, realtime);
        ewa = new User();
        ewa.setId(2L);
        ewa.setUsername("ewa-nowak");
    }

    @Test
    void everyMemberGetsTheirOwnCopyOfTheChat() {
        ChatDto forJanek = ChatDto.builder().id(5L).unreadCount(0).build();
        ChatDto forEwa = ChatDto.builder().id(5L).unreadCount(7).build();
        Map<Long, ChatDto> copies = new LinkedHashMap<>();
        copies.put(1L, forJanek);
        copies.put(2L, forEwa);
        when(chatService.getChatForEachMember(5L)).thenReturn(copies);

        notifier.chatChanged(5L);

        verify(realtime).publish("/topic/new-chats/1", Event.of(Event.CHAT_CHANGED, forJanek));
        verify(realtime).publish("/topic/new-chats/2", Event.of(Event.CHAT_CHANGED, forEwa));
    }

    @Test
    void aRemovedUserIsToldAndStopsFollowingTheChat() {
        notifier.removed(5L, ewa);

        verify(realtime).publish("/topic/removed-chats/2", Event.of(Event.CHAT_REMOVED, new ChatNotifier.Removed(5L)));
        verify(realtime).leaveChat(2L, 5L);
    }
}
