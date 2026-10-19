package com.example.connectmessenger.persistence;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole stack over the network: a real STOMP client (over the SockJS websocket transport, like the browser),
 * the security chain, the channel interceptor, the REST controllers and MySQL.
 */
class WebsocketIT extends MySqlIntegrationTest {
    @LocalServerPort int port;

    private final WebSocketStompClient stomp = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));

    private record Person(long id, String token) {
    }

    private Person ewa;
    private Person jan;
    private Person stranger;
    private long chatId;

    private Person register(String prefix) {
        String username = prefix + System.nanoTime();
        long id = RestAssured.given().port(port).contentType(ContentType.JSON)
                .body(Map.of("username", username, "name", prefix, "surname", "Test", "password", "correct horse 42"))
                .post("/api/v1/users").then().statusCode(201).extract().jsonPath().getLong("id");
        String token = RestAssured.given().port(port).contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", "correct horse 42"))
                .post("/api/v1/login").then().statusCode(200).extract().header("Authentication");
        return new Person(id, token);
    }

    private io.restassured.specification.RequestSpecification as(Person p) {
        return RestAssured.given().port(port).header("Authorization", p.token());
    }

    private StompSession connect(String token) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("Origin", "http://localhost:3000");
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) connectHeaders.add("Authentication", token);
        return stomp.connectAsync("http://localhost:" + port + "/websocket", handshake, connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private static BlockingQueue<Map<String, Object>> collect(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    @BeforeEach
    void people() {
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        ewa = register("ewa");
        jan = register("jan");
        stranger = register("stranger");
        chatId = as(ewa).contentType(ContentType.JSON).body(Map.of("userIds", List.of(ewa.id(), jan.id())))
                .post("/api/v1/chats").then().statusCode(200).extract().jsonPath().getLong("id");
    }

    private void say(Person who, String text) {
        as(who).multiPart("chatId", chatId).multiPart("type", "TEXT").multiPart("textContent", text)
                .post("/api/v1/messages").then().statusCode(200);
    }

    @Test
    void aMemberReceivesMessagesPostedOverRest() throws Exception {
        StompSession session = connect(jan.token());
        var messages = collect(session, "/topic/messages/" + chatId);
        Thread.sleep(300);  // the subscription must reach the broker before the message is published

        say(ewa, "hello over a real socket");

        Map<String, Object> message = messages.poll(10, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("content")).isEqualTo("hello over a real socket");
        assertThat(((Number) message.get("userId")).longValue()).isEqualTo(ewa.id());
        session.disconnect();
    }

    @Test
    void aMemberIsToldAboutANewChat() throws Exception {
        StompSession session = connect(stranger.token());
        var chats = collect(session, "/topic/new-chats/" + stranger.id());
        Thread.sleep(300);

        as(ewa).contentType(ContentType.JSON).body(Map.of("userIds", List.of(ewa.id(), stranger.id())))
                .post("/api/v1/chats").then().statusCode(200);

        assertThat(chats.poll(10, TimeUnit.SECONDS)).isNotNull();
        session.disconnect();
    }

    @Test
    void aStrangerCannotListenToAChat() throws Exception {
        StompSession session = connect(stranger.token());
        var messages = collect(session, "/topic/messages/" + chatId);
        Thread.sleep(300);

        say(ewa, "private");

        assertThat(messages.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectingWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> connect(null)).isInstanceOfAny(ExecutionException.class, TimeoutException.class);
        assertThatThrownBy(() -> connect("Bearer garbage")).isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }
}
