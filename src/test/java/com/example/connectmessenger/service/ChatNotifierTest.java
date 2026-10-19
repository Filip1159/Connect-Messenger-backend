package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.ChatDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotifierTest {
    @Mock
    ChatService chatService;
    @Mock
    SimpMessagingTemplate template;
    @Mock
    SimpUserRegistry registry;
    @Mock
    MessageChannel inbound;
    ChatNotifier notifier;
    User ewa;

    @BeforeEach
    void setUp() {
        notifier = new ChatNotifier(chatService, template, registry, inbound);
        ewa = new User();
        ewa.setId(2L);
        ewa.setUsername("ewa-nowak");
    }

    private static SimpSubscription subscription(String id, String destination) {
        SimpSubscription s = org.mockito.Mockito.mock(SimpSubscription.class);
        org.mockito.Mockito.lenient().when(s.getId()).thenReturn(id);
        when(s.getDestination()).thenReturn(destination);
        return s;
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

        verify(template).convertAndSend("/topic/new-chats/1", forJanek);
        verify(template).convertAndSend("/topic/new-chats/2", forEwa);
    }

    @Test
    void aRemovedUserIsToldAndStopsFollowingTheChat() {
        SimpSubscription messages = subscription("sub-0", "/topic/messages/5");
        SimpSubscription status = subscription("sub-1", "/topic/status/5");
        SimpSubscription otherChat = subscription("sub-2", "/topic/messages/6");
        SimpSession session = org.mockito.Mockito.mock(SimpSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.getSubscriptions()).thenReturn(Set.of(messages, status, otherChat));
        SimpUser connected = org.mockito.Mockito.mock(SimpUser.class);
        when(connected.getSessions()).thenReturn(Set.of(session));
        when(registry.getUser("ewa-nowak")).thenReturn(connected);

        notifier.removed(5L, ewa);

        verify(template).convertAndSend("/topic/removed-chats/2", new ChatNotifier.Removed(5L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<?>> sent = ArgumentCaptor.forClass(Message.class);
        verify(inbound, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(m -> {
            assertThat(SimpMessageHeaderAccessor.getMessageType(m.getHeaders())).isEqualTo(SimpMessageType.UNSUBSCRIBE);
            assertThat(SimpMessageHeaderAccessor.getSessionId(m.getHeaders())).isEqualTo("session-1");
        });
        assertThat(sent.getAllValues()).extracting(m -> SimpMessageHeaderAccessor.getSubscriptionId(m.getHeaders()))
                .containsExactlyInAnyOrder("sub-0", "sub-1");
    }

    @Test
    void anOfflineUserJustGetsTheNotification() {
        when(registry.getUser("ewa-nowak")).thenReturn(null);

        notifier.removed(5L, ewa);

        verify(template).convertAndSend(eq("/topic/removed-chats/2"), any(ChatNotifier.Removed.class));
        verify(inbound, never()).send(any());
    }
}
