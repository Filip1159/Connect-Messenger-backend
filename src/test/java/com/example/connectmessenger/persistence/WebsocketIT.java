package com.example.connectmessenger.persistence;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole stack over the network: a real Socket.IO client (the websocket transport, like the browser), the
 * authorizer, the REST controllers and MySQL.
 */
class WebsocketIT extends MySqlIntegrationTest {
    @LocalServerPort int port;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final List<Socket> sockets = new ArrayList<>();

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

    /** Connects and waits until the server has accepted the connection, or (for a refused one) closed it. */
    private Socket connect(String token, String origin) throws Exception {
        var options = IO.Options.builder()
                .setTransports(new String[]{"websocket"})
                .setReconnection(false)
                .setForceNew(true);
        if (token != null) options.setAuth(Map.of("token", token));
        if (origin != null) options.setExtraHeaders(Map.of("Origin", List.of(origin)));
        Socket socket = IO.socket(URI.create("http://localhost:" + REALTIME_PORT), options.build());
        sockets.add(socket);
        var connected = new CompletableFuture<Boolean>();
        socket.on(Socket.EVENT_CONNECT, args -> connected.complete(true));
        socket.on(Socket.EVENT_DISCONNECT, args -> connected.complete(false));
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> connected.complete(false));
        socket.connect();
        connected.get(10, TimeUnit.SECONDS);
        return socket;
    }

    private Socket connect(String token) throws Exception {
        return connect(token, null);
    }

    /** True once the server answered that the room may be followed. */
    private static boolean follow(Socket socket, String room) throws Exception {
        var answer = new CompletableFuture<Boolean>();
        socket.emit("subscribe", new Object[]{room}, args -> answer.complete(args[0].toString().contains("\"ok\":true")));
        return answer.get(10, TimeUnit.SECONDS);
    }

    private static BlockingQueue<Map<String, Object>> events(Socket socket) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        socket.on("event", args -> {
            try {
                received.add(JSON.readValue(args[0].toString(), new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                }));
            } catch (RuntimeException e) {
                throw new IllegalStateException(e);
            }
        });
        return received;
    }

    private Socket connectedAndFollowing(Person who, String... rooms) throws Exception {
        Socket socket = connect(who.token());
        for (String room : rooms) {
            assertThat(follow(socket, room)).as(room).isTrue();
        }
        return socket;
    }

    @BeforeEach
    void people() {
        ewa = register("ewa");
        jan = register("jan");
        stranger = register("stranger");
        chatId = as(ewa).contentType(ContentType.JSON).body(Map.of("userIds", List.of(ewa.id(), jan.id())))
                .post("/api/v1/chats").then().statusCode(200).extract().jsonPath().getLong("id");
    }

    @AfterEach
    void disconnectAll() {
        sockets.forEach(Socket::disconnect);
    }

    private void say(Person who, String text) {
        as(who).multiPart("chatId", chatId).multiPart("type", "TEXT").multiPart("textContent", text)
                .post("/api/v1/messages").then().statusCode(200);
    }

    @Test
    void aMemberReceivesMessagesPostedOverRest() throws Exception {
        var socket = connectedAndFollowing(jan, "/topic/messages/" + chatId);
        var messages = events(socket);

        say(ewa, "hello over a real socket");

        Map<String, Object> message = messages.poll(10, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.get("type")).isEqualTo("message.created");
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) message.get("payload");
        assertThat(payload.get("content")).isEqualTo("hello over a real socket");
        assertThat(((Number) payload.get("userId")).longValue()).isEqualTo(ewa.id());
        assertThat(payload.get("time")).isInstanceOf(String.class);  // dates are ISO strings, as in REST
    }

    @Test
    void aMemberIsToldAboutANewChat() throws Exception {
        var socket = connectedAndFollowing(stranger, "/topic/new-chats/" + stranger.id());
        var chats = events(socket);

        as(ewa).contentType(ContentType.JSON).body(Map.of("userIds", List.of(ewa.id(), stranger.id())))
                .post("/api/v1/chats").then().statusCode(200);

        var event = chats.poll(10, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("chat.changed");
        assertThat(event.get("payload")).isNotNull();
    }

    @Test
    void editsDeletionsAndReactionsAreTypedEvents() throws Exception {
        var events = events(connectedAndFollowing(jan, "/topic/messages/" + chatId));
        long id = as(ewa).multiPart("chatId", chatId).multiPart("type", "TEXT").multiPart("textContent", "first")
                .post("/api/v1/messages").then().statusCode(200).extract().jsonPath().getLong("id");
        as(ewa).contentType(ContentType.JSON).body(Map.of("textContent", "second")).patch("/api/v1/messages/" + id).then().statusCode(200);
        as(jan).contentType(ContentType.JSON).body(Map.of("emoji", "👍")).put("/api/v1/messages/" + id + "/reaction").then().statusCode(200);
        as(ewa).delete("/api/v1/messages/" + id).then().statusCode(200);

        List<Object> types = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            types.add(event.get("type"));
        }
        assertThat(types).containsExactly("message.created", "message.edited", "message.reacted", "message.deleted");
    }

    @Test
    void readStatusesAndTypingAreTypedEvents() throws Exception {
        var janSocket = connectedAndFollowing(jan, "/topic/status/" + chatId, "/topic/typing/" + chatId);
        var received = events(janSocket);

        as(ewa).contentType(ContentType.JSON).body(Map.of("id", Map.of("chatId", chatId))).put("/api/v1/status").then().statusCode(200);
        connect(ewa.token()).emit("typing", chatId);

        var types = new ArrayList<Object>();
        for (int i = 0; i < 2; i++) {
            var event = received.poll(10, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            types.add(event.get("type"));
        }
        assertThat(types).containsExactlyInAnyOrder("status.updated", "typing");
    }

    @Test
    void presenceFollowsTheSocketsOfAUser() throws Exception {
        var events = events(connectedAndFollowing(jan, "/topic/presence/" + ewa.id()));

        var ewaSocket = connect(ewa.token());
        var online = events.poll(10, TimeUnit.SECONDS);
        assertThat(online).containsEntry("type", "presence.changed");
        ewaSocket.disconnect();
        var offline = events.poll(10, TimeUnit.SECONDS);
        assertThat(offline).isNotNull();
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) offline.get("payload");
        assertThat(payload).containsEntry("online", false);
        assertThat(payload.get("lastSeen")).isNotNull();
    }

    @Test
    void aStrangerCannotListenToAChat() throws Exception {
        var socket = connect(stranger.token());
        var messages = events(socket);

        assertThat(follow(socket, "/topic/messages/" + chatId)).isFalse();
        assertThat(follow(socket, "/topic/new-chats/" + ewa.id())).isFalse();
        assertThat(follow(socket, "/topic/nonsense")).isFalse();
        say(ewa, "private");

        assertThat(messages.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void aMemberRemovedFromAGroupStopsReceivingItsMessages() throws Exception {
        long group = as(ewa).contentType(ContentType.JSON)
                .body(Map.of("userIds", List.of(ewa.id(), jan.id(), stranger.id()), "name", "Trip"))
                .post("/api/v1/chats").then().statusCode(200).extract().jsonPath().getLong("id");
        var events = events(connectedAndFollowing(stranger, "/topic/messages/" + group));

        as(ewa).delete("/api/v1/chats/" + group + "/members/" + stranger.id()).then().statusCode(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(204)));
        as(ewa).multiPart("chatId", group).multiPart("type", "TEXT").multiPart("textContent", "after")
                .post("/api/v1/messages").then().statusCode(200);

        assertThat(events.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectingWithoutAValidTokenIsRefused() throws Exception {
        for (String token : new String[]{null, "Bearer garbage"}) {
            Socket socket = connect(token);
            // a wrong token is refused at once, a missing one after a few seconds
            org.awaitility.Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(socket.connected()).as("token " + token).isFalse());
        }
    }

    @Test
    void aPageFromAnotherSiteIsRefused() throws Exception {
        Socket socket = connect(jan.token(), "http://evil.example");

        assertThat(socket.connected()).isFalse();
    }
}
