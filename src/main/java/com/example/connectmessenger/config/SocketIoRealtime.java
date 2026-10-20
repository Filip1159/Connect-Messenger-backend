package com.example.connectmessenger.config;

import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
import com.corundumstudio.socketio.namespace.Namespace;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.Event;
import com.example.connectmessenger.model.dto.TypingDto;
import com.example.connectmessenger.service.Realtime;
import com.example.connectmessenger.service.Rooms;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Socket.IO server (netty-socketio; it listens on its own port, `realtime.port`).
 * <p>
 * Protocol: the client connects with `auth: { token: "Bearer <jwt>" }` and is refused when the token is wrong. It then sends `subscribe` / `unsubscribe` with a room name (see {@link Rooms}); `subscribe` is acknowledged
 * with `{ok: true|false}`. The only other thing it may send is `typing` with a chat id. Everything the server pushes
 * is the event `event` with the argument `{type, payload}` (see {@link Event}).
 */
@Slf4j
@Component
public class SocketIoRealtime implements Realtime, SmartLifecycle {
    static final String EVENT = "event";
    private static final Duration AUTH_GRACE = Duration.ofSeconds(5);

    private final RealtimeAuthorizer authorizer;
    private final ApplicationEventPublisher events;
    private final JsonMapper mapper;
    private final boolean enabled;
    private final int port;

    private final Map<UUID, User> users = new ConcurrentHashMap<>();  // connected clients (their token was valid)
    private final ScheduledExecutorService graceTimer = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "realtime-auth-grace");
        thread.setDaemon(true);
        return thread;
    });
    private volatile SocketIOServer server;

    public SocketIoRealtime(RealtimeAuthorizer authorizer, ApplicationEventPublisher events, JsonMapper mapper,
                            @Value("${realtime.enabled:true}") boolean enabled, @Value("${realtime.port:9092}") int port) {
        this.authorizer = authorizer;
        this.events = events;
        this.mapper = mapper;
        this.enabled = enabled;
        this.port = port;
    }

    @Override
    public void start() {
        if (!enabled) {
            return;
        }
        var config = new Configuration();
        config.setPort(port);
        config.setTransports(Transport.WEBSOCKET);
        // a browser sends its Origin with the handshake; refuse pages of other sites
        config.setAuthorizationListener(handshake -> authorizer.originAllowed(handshake.getHttpHeaders().get("Origin"))
                ? AuthorizationResult.SUCCESSFUL_AUTHORIZATION : AuthorizationResult.FAILED_AUTHORIZATION);

        var created = new SocketIOServer(config);
        created.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(this::onAuth);
        created.addConnectListener(this::onConnect);
        created.addDisconnectListener(this::onDisconnect);
        created.addEventListener("subscribe", String.class, (client, room, ack) -> {
            User user = users.get(client.getSessionId());
            boolean allowed = user != null && authorizer.mayFollow(user, room);
            if (allowed) {
                client.joinRoom(room);
            }
            if (ack.isAckRequested()) {
                ack.sendAckData(Map.of("ok", allowed));
            }
        });
        created.addEventListener("unsubscribe", String.class, (client, room, ack) -> client.leaveRoom(room));
        created.addEventListener("typing", Long.class, (client, chatId, ack) -> {
            User user = users.get(client.getSessionId());
            if (user != null && authorizer.mayType(user, chatId)) {
                publish(Rooms.typing(chatId), Event.of(Event.TYPING, new TypingDto(chatId, user.getId())));
            }
        });
        created.start();
        server = created;
        log.info("Socket.IO server listening on port {}", port);
    }

    /** The token comes with the connect packet (after the transport is up); a connection with a wrong one is refused. */
    private AuthTokenResult onAuth(Object auth, SocketIOClient client) {
        var user = authorizer.authenticate(tokenOf(auth)).orElse(null);
        if (user == null) {
            return new AuthTokenResult(false, Map.of("message", "unauthorized"));
        }
        users.put(client.getSessionId(), user);
        events.publishEvent(new SessionOpened(client.getSessionId().toString(), user.getId()));
        return AuthTokenResult.AuthTokenResultSuccess;
    }

    /** A client that sent no token at all would otherwise stay connected, so it gets a few seconds to authenticate. */
    private void onConnect(SocketIOClient client) {
        graceTimer.schedule(() -> {
            if (!users.containsKey(client.getSessionId())) {
                client.disconnect();
            }
        }, AUTH_GRACE.toSeconds(), TimeUnit.SECONDS);
    }

    private void onDisconnect(SocketIOClient client) {
        if (users.remove(client.getSessionId()) != null) {
            events.publishEvent(new SessionClosed(client.getSessionId().toString()));
        }
    }

    private static String tokenOf(Object auth) {
        return auth instanceof Map<?, ?> map && map.get("token") instanceof String token ? token : null;
    }

    @Override
    public void publish(String room, Event<?> event) {
        var running = server;
        if (running != null) {
            running.getRoomOperations(room).sendEvent(EVENT, mapper.convertValue(event, Map.class));
        }
    }

    @Override
    public void leaveChat(Long userId, Long chatId) {
        var running = server;
        if (running == null) {
            return;
        }
        for (SocketIOClient client : running.getAllClients()) {
            User user = users.get(client.getSessionId());
            if (user != null && user.getId().equals(userId)) {
                client.leaveRoom(Rooms.messages(chatId));
                client.leaveRoom(Rooms.status(chatId));
                client.leaveRoom(Rooms.typing(chatId));
            }
        }
    }

    @Override
    public void stop() {
        var running = server;
        server = null;
        if (running != null) {
            running.stop();
        }
        users.clear();
        graceTimer.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }
}
