package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.service.ChatService;
import com.example.connectmessenger.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsocketAuthChannelInterceptorTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    @Mock
    UserService userService;
    @Mock
    ChatService chatService;
    WebsocketAuthChannelInterceptor interceptor;
    User user;

    @BeforeEach
    void setUp() {
        interceptor = new WebsocketAuthChannelInterceptor(
                new CustomProperties(SECRET, 3600000, List.of(), "bucket", "eu-central-1"), userService, chatService);
        user = new User();
        user.setId(1L);
        user.setUsername("janek007");
    }

    private static String token(String secret, Date expiresAt) {
        return "Bearer " + JWT.create().withClaim("username", "janek007")
                .withExpiresAt(expiresAt).sign(Algorithm.HMAC256(secret));
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3600_000);
    }

    private static Message<?> connect(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) accessor.addNativeHeader("Authentication", authHeader);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> frame(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (principal != null) accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal authenticated() {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void subscribe(String destination) {
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, authenticated()), null);
    }

    @Test
    void connectWithValidTokenAttachesTheUserToTheSession() {
        when(userService.loadUserByUsername("janek007")).thenReturn(user);

        Message<?> result = interceptor.preSend(connect(token(SECRET, inOneHour())), null);

        var principal = StompHeaderAccessor.wrap(result).getUser();
        assertThat(principal).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) principal).getPrincipal()).isSameAs(user);
    }

    @Test
    void connectIsRejectedWithoutAValidToken() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Token abc"), null)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer garbage"), null)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect(token("another-secret-another-secret-12345", inOneHour())), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect(token(SECRET, new Date(System.currentTimeMillis() - 1000))), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectIsRejectedForUnknownUser() {
        when(userService.loadUserByUsername("janek007")).thenThrow(new UsernameNotFoundException("gone"));

        assertThatThrownBy(() -> interceptor.preSend(connect(token(SECRET, inOneHour())), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void membersMaySubscribeToTheirChat() {
        assertThatCode(() -> subscribe("/topic/messages/5")).doesNotThrowAnyException();
    }

    @Test
    void nonMembersMayNotSubscribeToAChat() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(chatService).assertMember(7L, 1L);

        assertThatThrownBy(() -> subscribe("/topic/messages/7")).isInstanceOf(MessagingException.class);
    }

    @Test
    void usersMaySubscribeOnlyToTheirOwnNewChatsTopic() {
        assertThatCode(() -> subscribe("/topic/new-chats/1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> subscribe("/topic/new-chats/2")).isInstanceOf(MessagingException.class);
    }

    @Test
    void unknownOrMalformedDestinationsAreDenied() {
        assertThatThrownBy(() -> subscribe("/topic/anything")).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> subscribe("/queue/x")).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> subscribe("/topic/messages/abc")).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> subscribe("/topic/messages/")).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> subscribe(null)).isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeWithoutAnAuthenticatedUserIsDenied() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/messages/5", null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void clientsCannotSendFrames() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/messages/5", authenticated()), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void otherFramesPassThrough() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.DISCONNECT, null, authenticated()), null))
                .doesNotThrowAnyException();
    }
}
