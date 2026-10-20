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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeAuthorizerTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    @Mock
    UserService userService;
    @Mock
    ChatService chatService;
    RealtimeAuthorizer authorizer;
    User user;

    @BeforeEach
    void setUp() {
        authorizer = new RealtimeAuthorizer(
                new CustomProperties(SECRET, 3600000, List.of("http://localhost:3000"), "bucket", "eu-central-1", 5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                userService, chatService);
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

    @Test
    void aValidTokenIdentifiesTheUser() {
        when(userService.loadUserByUsername("janek007")).thenReturn(user);

        assertThat(authorizer.authenticate(token(SECRET, inOneHour()))).containsSame(user);
    }

    @Test
    void connectIsRefusedWithoutAValidToken() {
        assertThat(authorizer.authenticate(null)).isEmpty();
        assertThat(authorizer.authenticate("Token abc")).isEmpty();
        assertThat(authorizer.authenticate("Bearer garbage")).isEmpty();
        assertThat(authorizer.authenticate(token("another-secret-another-secret-12345", inOneHour()))).isEmpty();
        assertThat(authorizer.authenticate(token(SECRET, new Date(System.currentTimeMillis() - 1000)))).isEmpty();
    }

    @Test
    void connectIsRefusedForAnUnknownUser() {
        when(userService.loadUserByUsername("janek007")).thenThrow(new UsernameNotFoundException("gone"));

        assertThat(authorizer.authenticate(token(SECRET, inOneHour()))).isEmpty();
    }

    @Test
    void onlyKnownOriginsAndClientsWithoutOneMayConnect() {
        assertThat(authorizer.originAllowed("http://localhost:3000")).isTrue();
        assertThat(authorizer.originAllowed(null)).isTrue();
        assertThat(authorizer.originAllowed("http://evil.example")).isFalse();
    }

    @Test
    void membersMayFollowTheirChat() {
        assertThat(authorizer.mayFollow(user, "/topic/messages/5")).isTrue();
        assertThat(authorizer.mayFollow(user, "/topic/status/5")).isTrue();
        assertThat(authorizer.mayFollow(user, "/topic/typing/5")).isTrue();
    }

    @Test
    void nonMembersMayNotFollowAChat() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(chatService).assertMember(7L, 1L);

        assertThat(authorizer.mayFollow(user, "/topic/messages/7")).isFalse();
        assertThat(authorizer.mayFollow(user, "/topic/status/7")).isFalse();
        assertThat(authorizer.mayFollow(user, "/topic/typing/7")).isFalse();
    }

    @Test
    void presenceIsVisibleOnlyForUsersSharingAChat() {
        when(chatService.sharesChat(1L, 2L)).thenReturn(true);
        assertThat(authorizer.mayFollow(user, "/topic/presence/2")).isTrue();
        assertThat(authorizer.mayFollow(user, "/topic/presence/3")).isFalse();
    }

    @Test
    void onlyTypingInOwnChatsMayBeSent() {
        assertThat(authorizer.mayType(user, 5L)).isTrue();
        assertThat(authorizer.mayType(user, null)).isFalse();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(chatService).assertMember(7L, 1L);
        assertThat(authorizer.mayType(user, 7L)).isFalse();
    }

    @Test
    void usersMayFollowOnlyTheirOwnChatListRooms() {
        assertThat(authorizer.mayFollow(user, "/topic/new-chats/1")).isTrue();
        assertThat(authorizer.mayFollow(user, "/topic/new-chats/2")).isFalse();
        assertThat(authorizer.mayFollow(user, "/topic/removed-chats/1")).isTrue();
        assertThat(authorizer.mayFollow(user, "/topic/removed-chats/2")).isFalse();
    }

    @Test
    void unknownOrMalformedRoomsAreDenied() {
        assertThat(authorizer.mayFollow(user, "/topic/anything")).isFalse();
        assertThat(authorizer.mayFollow(user, "/queue/x")).isFalse();
        assertThat(authorizer.mayFollow(user, "/topic/messages/abc")).isFalse();
        assertThat(authorizer.mayFollow(user, "/topic/messages/")).isFalse();
        assertThat(authorizer.mayFollow(user, null)).isFalse();
    }
}
