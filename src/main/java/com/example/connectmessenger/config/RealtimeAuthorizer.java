package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.Rooms;
import com.example.connectmessenger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Decides who may connect to the Socket.IO server and which rooms they may follow. */
@Service
@RequiredArgsConstructor
public class RealtimeAuthorizer {
    private final CustomProperties properties;
    private final UserService userService;
    private final ChatService chatService;

    /** The user a "Bearer ..." token belongs to; empty when the token is missing, wrong or expired. */
    public Optional<User> authenticate(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            String username = JWT.require(Algorithm.HMAC256(properties.secret()))
                    .build()
                    .verify(token.substring("Bearer ".length()))
                    .getClaim("username").asString();
            return Optional.of((User) userService.loadUserByUsername(username));
        } catch (JWTVerificationException | UsernameNotFoundException e) {
            return Optional.empty();
        }
    }

    /** Whether the Origin of a browser handshake is one of the allowed ones; other clients send none. */
    public boolean originAllowed(String origin) {
        return origin == null || properties.allowedOrigins().contains(origin);
    }

    /**
     * Clients talk to the server over REST; the only thing they may send is the typing notification of a chat they
     * belong to (and the subscriptions below).
     */
    public boolean mayType(User user, Long chatId) {
        return chatId != null && isMember(chatId, user);
    }

    public boolean mayFollow(User user, String room) {
        if (room == null) {
            return false;
        }
        try {
            if (room.startsWith(Rooms.MESSAGES)) {
                return isMember(Long.parseLong(room.substring(Rooms.MESSAGES.length())), user);
            } else if (room.startsWith(Rooms.STATUS)) {
                return isMember(Long.parseLong(room.substring(Rooms.STATUS.length())), user);
            } else if (room.startsWith(Rooms.TYPING)) {
                return isMember(Long.parseLong(room.substring(Rooms.TYPING.length())), user);
            } else if (room.startsWith(Rooms.PRESENCE)) {
                // presence is only visible to people you share a chat with
                return chatService.sharesChat(user.getId(), Long.parseLong(room.substring(Rooms.PRESENCE.length())));
            } else if (room.startsWith(Rooms.NEW_CHATS)) {
                return Long.parseLong(room.substring(Rooms.NEW_CHATS.length())) == user.getId();
            } else if (room.startsWith(Rooms.REMOVED_CHATS)) {
                return Long.parseLong(room.substring(Rooms.REMOVED_CHATS.length())) == user.getId();
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isMember(Long chatId, User user) {
        try {
            chatService.assertMember(chatId, user.getId());
            return true;
        } catch (ResponseStatusException e) {
            return false;
        }
    }
}
