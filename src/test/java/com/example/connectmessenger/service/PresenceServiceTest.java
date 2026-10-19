package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.PresenceDto;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {
    @Mock
    UserRepo userRepo;
    @Mock
    ChatRepo chatRepo;
    @Mock
    SimpMessagingTemplate template;
    @InjectMocks
    PresenceService presence;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void userIsOnlineWhileAnySessionIsOpen() {
        presence.sessionOpened("a", 1L);
        presence.sessionOpened("b", 1L);
        verify(template).convertAndSend("/topic/presence/1", new PresenceDto(1L, true, null));  // only for the first one

        presence.sessionClosed("a");
        assertThat(presence.isOnline(1L)).isTrue();
        verify(userRepo, never()).save(any());

        when(userRepo.findById(1L)).thenReturn(Optional.of(user(1)));
        presence.sessionClosed("b");
        assertThat(presence.isOnline(1L)).isFalse();
        verify(userRepo).save(any(User.class));
        verify(template).convertAndSend(eq("/topic/presence/1"), org.mockito.ArgumentMatchers.<Object>argThat(
                o -> o instanceof PresenceDto d && !d.online() && d.lastSeen() != null));
    }

    @Test
    void closingAnUnknownSessionIsIgnored() {
        presence.sessionClosed("unknown");
        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void reportsPresenceOfContactsOnly() {
        var me = user(1);
        var ewa = user(2);
        ewa.setLastSeen(LocalDateTime.parse("2021-07-07T22:00:00"));
        var john = user(3);
        when(chatRepo.findByUsers_Id(1L)).thenReturn(List.of(
                Chat.builder().id(1L).users(List.of(me, ewa)).build(),
                Chat.builder().id(2L).users(List.of(me, john)).build(),
                Chat.builder().id(3L).users(List.of(me, ewa)).build()));
        presence.sessionOpened("s", 3L);

        assertThat(presence.presenceOfContacts(1L)).containsExactly(
                new PresenceDto(2L, false, ewa.getLastSeen()),
                new PresenceDto(3L, true, null));
    }
}
