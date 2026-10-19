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

@Service
@RequiredArgsConstructor
public class WebsocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String MESSAGES_TOPIC = "/topic/messages/";
    private static final String NEW_CHATS_TOPIC = "/topic/new-chats/";

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
            // clients only talk to the server over REST; a client SEND to /topic/** would be
            // re-broadcast by the simple broker to every subscriber
            throw new MessagingException("Sending frames is not allowed");
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

    private void authorizeSubscription(StompHeaderAccessor headerAccessor) {
        if (!(headerAccessor.getUser() instanceof Authentication auth) || !(auth.getPrincipal() instanceof User user)) {
            throw new MessagingException("Not authenticated");
        }
        String destination = headerAccessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Missing destination");
        }

        try {
            if (destination.startsWith(MESSAGES_TOPIC)) {
                chatService.assertMember(Long.parseLong(destination.substring(MESSAGES_TOPIC.length())), user.getId());
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
