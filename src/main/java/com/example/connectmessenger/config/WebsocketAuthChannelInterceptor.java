package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WebsocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String MESSAGES_TOPIC = "/topic/messages/";
    private static final String STATUS_TOPIC = "/topic/status/";
    private static final String NEW_CHATS_TOPIC = "/topic/new-chats/";
    private static final String TYPING_TOPIC = "/topic/typing/";
    private static final String PRESENCE_TOPIC = "/topic/presence/";
    private static final Pattern TYPING_DESTINATION = Pattern.compile("^/app/chats/(\\d+)/typing$");

    private final CustomProperties properties;
    private final UserService userService;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headerAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headerAccessor == null || headerAccessor.getCommand() == null) {
            return message;
        }

        StompCommand command = headerAccessor.getCommand();
        if (command == StompCommand.CONNECT) {
            headerAccessor.setUser(authenticate(headerAccessor));
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(headerAccessor);
        } else if (command == StompCommand.SEND) {
            authorizeSend(headerAccessor);
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor headerAccessor) {
        String token = headerAccessor.getFirstNativeHeader("Authentication");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new MessagingException("Received no or incorrect token");
        }

        try {
            String username = JWT.require(Algorithm.HMAC256(properties.secret()))
                    .build()
                    .verify(token.substring("Bearer ".length()))
                    .getClaim("username").asString();
            User user = (User) userService.loadUserByUsername(username);
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } catch (JWTVerificationException | UsernameNotFoundException e) {
            throw new MessagingException("Invalid token");
        }
    }

    /**
     * Clients talk to the server over REST; the only frame they may send is the typing notification of a chat they
     * belong to. A client SEND to /topic/** would be re-broadcast by the simple broker to every subscriber.
     */
    private void authorizeSend(StompHeaderAccessor headerAccessor) {
        User user = authenticatedUser(headerAccessor);
        String destination = headerAccessor.getDestination();
        var typing = destination == null ? null : TYPING_DESTINATION.matcher(destination);
        if (typing == null || !typing.matches()) {
            throw new MessagingException("Sending frames is not allowed");
        }
        try {
            chatService.assertMember(Long.parseLong(typing.group(1)), user.getId());
        } catch (NumberFormatException | org.springframework.web.server.ResponseStatusException e) {
            throw new MessagingException("Not a member of this chat");
        }
    }

    private static User authenticatedUser(StompHeaderAccessor headerAccessor) {
        if (!(headerAccessor.getUser() instanceof Authentication auth) || !(auth.getPrincipal() instanceof User user)) {
            throw new MessagingException("Not authenticated");
        }
        return user;
    }

    private void authorizeSubscription(StompHeaderAccessor headerAccessor) {
        User user = authenticatedUser(headerAccessor);
        String destination = headerAccessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Missing destination");
        }

        try {
            if (destination.startsWith(MESSAGES_TOPIC)) {
                chatService.assertMember(Long.parseLong(destination.substring(MESSAGES_TOPIC.length())), user.getId());
            } else if (destination.startsWith(STATUS_TOPIC)) {
                chatService.assertMember(Long.parseLong(destination.substring(STATUS_TOPIC.length())), user.getId());
            } else if (destination.startsWith(TYPING_TOPIC)) {
                chatService.assertMember(Long.parseLong(destination.substring(TYPING_TOPIC.length())), user.getId());
            } else if (destination.startsWith(PRESENCE_TOPIC)) {
                // presence is only visible to people you share a chat with
                if (!chatService.sharesChat(user.getId(), Long.parseLong(destination.substring(PRESENCE_TOPIC.length())))) {
                    throw new MessagingException("Not allowed to follow this user");
                }
            } else if (destination.startsWith(NEW_CHATS_TOPIC)) {
                if (Long.parseLong(destination.substring(NEW_CHATS_TOPIC.length())) != user.getId()) {
                    throw new MessagingException("Cannot subscribe to another user's topic");
                }
            } else {
                throw new MessagingException("Unknown destination");
            }
        } catch (NumberFormatException e) {
            throw new MessagingException("Invalid destination");
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw new MessagingException("Not a member of this chat");
        }
    }
}
