package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.PresenceDto;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks who is connected over the websocket; a user is online while at least one session is open. */
@Service
@RequiredArgsConstructor
public class PresenceService {
    private final UserRepo userRepo;
    private final ChatRepo chatRepo;
    private final SimpMessagingTemplate template;

    private final Object lock = new Object();
    private final Map<String, Long> sessionUsers = new HashMap<>();  // websocket session id -> user id
    private final Map<Long, Integer> sessionCounts = new HashMap<>();  // user id -> open sessions

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long userId = userIdOf(event.getUser());
        if (userId == null) {
            userId = userIdOf(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        }
        if (userId != null) {
            sessionOpened(StompHeaderAccessor.wrap(event.getMessage()).getSessionId(), userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessionClosed(event.getSessionId());
    }

    void sessionOpened(String sessionId, Long userId) {
        boolean first;
        synchronized (lock) {
            if (sessionUsers.put(sessionId, userId) != null) {
                return;
            }
            first = sessionCounts.merge(userId, 1, Integer::sum) == 1;
        }
        if (first) {
            template.convertAndSend("/topic/presence/" + userId, new PresenceDto(userId, true, null));
        }
    }

    void sessionClosed(String sessionId) {
        Long userId;
        boolean last;
        synchronized (lock) {
            userId = sessionUsers.remove(sessionId);
            if (userId == null) {
                return;
            }
            last = sessionCounts.merge(userId, -1, Integer::sum) <= 0;
            if (last) {
                sessionCounts.remove(userId);
            }
        }
        if (last) {
            var seen = LocalDateTime.now();
            userRepo.findById(userId).ifPresent(u -> {
                u.setLastSeen(seen);
                userRepo.save(u);
            });
            template.convertAndSend("/topic/presence/" + userId, new PresenceDto(userId, false, seen));
        }
    }

    public boolean isOnline(Long userId) {
        synchronized (lock) {
            return sessionCounts.containsKey(userId);
        }
    }

    /** Presence of everybody the user shares a chat with. */
    @Transactional(readOnly = true)
    public List<PresenceDto> presenceOfContacts(Long userId) {
        Map<Long, User> contacts = new LinkedHashMap<>();
        for (Chat chat : chatRepo.findByUsers_Id(userId)) {
            chat.getUsers().stream().filter(u -> !u.getId().equals(userId)).forEach(u -> contacts.putIfAbsent(u.getId(), u));
        }
        return contacts.values().stream()
                .map(u -> new PresenceDto(u.getId(), isOnline(u.getId()), u.getLastSeen()))
                .toList();
    }

    private static Long userIdOf(java.security.Principal principal) {
        return principal instanceof Authentication auth && auth.getPrincipal() instanceof User user ? user.getId() : null;
    }
}
