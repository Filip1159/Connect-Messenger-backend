package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.Reaction;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.ReactionRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.repo.UserRepo;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base for controller component tests: the whole application (security chain, filters, controllers, services,
 * serialization) behind a real embedded server, driven over HTTP with RestAssured. There is no database -
 * JPA / DataSource auto-configuration is excluded (application-test.properties) and the four repositories and
 * S3 are Mockito beans stubbed in {@link #stubRepositories()} with the data below. Since no persistence layer
 * runs, entity mapping and queries are NOT covered here.
 *
 * Data: janek007=1/qwerty, ewa-nowak=2, mrsmith=3, grace01=4/1234;
 * chat 1 = {1,2}, chat 2 = {1,3}, chat 3 = {4,3}, chat 4 = {1,4}; message 1 is in chat 1, message 13 in chat 2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ControllerComponentTest.NoOpTransactions.class)
abstract class ControllerComponentTest {
    static final String SECRET = "test-secret-test-secret-test-secret-1234";
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    /** Services are @Transactional but there is no JPA, so give them a transaction manager that does nothing. */
    @TestConfiguration
    static class NoOpTransactions {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }

    private static final String ENCODED_QWERTY = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("qwerty");
    private static final String ENCODED_1234 = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("1234");

    static final User JANEK = user(1, "janek007", "Janek", "Kowalski", ENCODED_QWERTY);
    static final User EWA = user(2, "ewa-nowak", "Ewa", "Nowak", "{noop}abcd");
    static final User MRSMITH = user(3, "mrsmith", "John", "Smith", "{noop}hahaxd");
    static final User GRACE = user(4, "grace01", "Grace", "Hopper", ENCODED_1234);
    static final List<User> USERS = List.of(JANEK, EWA, MRSMITH, GRACE);

    /** chat id -> member user ids */
    static final Map<Long, Set<Long>> MEMBERS = Map.of(1L, Set.of(1L, 2L), 2L, Set.of(1L, 3L), 3L, Set.of(4L, 3L), 4L, Set.of(1L, 4L));

    @LocalServerPort
    int port;
    @MockitoBean
    S3Client s3Client;
    @MockitoBean
    UserRepo userRepo;
    @MockitoBean
    ChatRepo chatRepo;
    @MockitoBean
    MessageRepo messageRepo;
    @MockitoBean
    StatusRepo statusRepo;
    @MockitoBean
    ReactionRepo reactionRepo;

    /** The reactions "table" of the current test. */
    final List<Reaction> reactions = new CopyOnWriteArrayList<>();

    @BeforeEach
    void stubRepositories() {
        RestAssured.port = port;

        when(userRepo.getUserByUsername(anyString())).thenAnswer(inv ->
                USERS.stream().filter(u -> u.getUsername().equals(inv.getArgument(0))).findFirst());
        when(userRepo.findById(anyLong())).thenAnswer(inv ->
                USERS.stream().filter(u -> u.getId().equals(inv.getArgument(0))).findFirst());
        when(userRepo.findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
                anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String q = ((String) inv.getArgument(0)).toLowerCase();
            return USERS.stream().filter(u -> u.getUsername().toLowerCase().contains(q)
                    || u.getName().toLowerCase().contains(q) || u.getSurname().toLowerCase().contains(q)).toList();
        });

        when(chatRepo.existsByIdAndUsers_Id(any(), any())).thenAnswer(inv ->
                MEMBERS.getOrDefault(inv.<Long>getArgument(0), Set.of()).contains(inv.<Long>getArgument(1)));
        when(chatRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(chat(inv.getArgument(0))));
        when(chatRepo.findByUsers_Id(anyLong())).thenAnswer(inv -> MEMBERS.entrySet().stream()
                .filter(e -> e.getValue().contains(inv.<Long>getArgument(0)))
                .map(e -> chat(e.getKey())).toList());
        var nextChatId = new AtomicLong(5);
        when(chatRepo.save(any(Chat.class))).thenAnswer(inv -> {
            Chat chat = inv.getArgument(0);
            chat.setId(nextChatId.getAndIncrement());
            return chat;
        });

        when(messageRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(message(inv.getArgument(0))));
        var nextMessageId = new AtomicLong(100);
        when(messageRepo.save(any(Message.class))).thenAnswer(inv -> {
            Message message = inv.getArgument(0);
            message.setId(nextMessageId.getAndIncrement());
            return message;
        });
        when(messageRepo.findByTypeAndContent(any(), anyString())).thenReturn(Optional.empty());
        when(messageRepo.findByChatIdAndIdLessThanOrderByIdDesc(anyLong(), anyLong(), any())).thenAnswer(inv -> {
            long chatId = inv.getArgument(0);
            long before = inv.getArgument(1);
            return IntStream.rangeClosed(1, 120).boxed().sorted(Comparator.reverseOrder())
                    .filter(i -> i < before)
                    .map(i -> new Message((long) i, chatId, 1L, LocalDateTime.parse("2021-07-07T22:00:00"), MessageType.TEXT, "m" + i, null, null, null))
                    .limit(inv.<Limit>getArgument(2).max())
                    .toList();
        });

        when(messageRepo.findByChatIdAndIdGreaterThanOrderByIdAsc(anyLong(), anyLong(), any())).thenAnswer(inv -> {
            long chatId = inv.getArgument(0);
            long after = inv.getArgument(1);
            return IntStream.rangeClosed(1, 120).boxed()
                    .filter(i -> i > after)
                    .map(i -> new Message((long) i, chatId, 1L, LocalDateTime.parse("2021-07-07T22:00:00"), MessageType.TEXT, "m" + i, null, null, null))
                    .limit(inv.<Limit>getArgument(2).max())
                    .toList();
        });
        // search: messages m1..m120, newest first, filtered by the (lower-cased) text
        when(messageRepo.findByChatIdAndTypeAndDeletedAtIsNullAndContentContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                anyLong(), any(), anyString(), anyLong(), any())).thenAnswer(inv -> {
            long chatId = inv.getArgument(0);
            String text = ((String) inv.getArgument(2)).toLowerCase();
            long before = inv.getArgument(3);
            return IntStream.rangeClosed(1, 120).boxed().sorted(Comparator.reverseOrder())
                    .filter(i -> i < before && ("m" + i).contains(text))
                    .map(i -> new Message((long) i, chatId, 1L, LocalDateTime.parse("2021-07-07T22:00:00"), MessageType.TEXT, "m" + i, null, null, null))
                    .limit(inv.<Limit>getArgument(4).max())
                    .toList();
        });
        // unread = 120 messages minus the last seen one (statuses carry no message id here, so everything is unread)
        when(messageRepo.countByChatIdAndIdGreaterThanAndUserIdNot(anyLong(), anyLong(), anyLong())).thenReturn(3L);
        when(chatRepo.sharesChat(anyLong(), anyLong())).thenAnswer(inv -> MEMBERS.values().stream()
                .anyMatch(m -> m.contains(inv.<Long>getArgument(0)) && m.contains(inv.<Long>getArgument(1))));

        reactions.clear();
        var nextReactionId = new AtomicLong(1);
        when(reactionRepo.save(any(Reaction.class))).thenAnswer(inv -> {
            Reaction reaction = inv.getArgument(0);
            if (reaction.getId() == null) {
                reaction.setId(nextReactionId.getAndIncrement());
                reactions.add(reaction);
            }
            return reaction;
        });
        when(reactionRepo.findByMessageIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> reactions.stream()
                .filter(r -> r.getMessageId().equals(inv.getArgument(0)) && r.getUserId().equals(inv.getArgument(1)))
                .findFirst());
        when(reactionRepo.findByMessageIdIn(any())).thenAnswer(inv -> reactions.stream()
                .filter(r -> inv.<java.util.Collection<Long>>getArgument(0).contains(r.getMessageId())).toList());
        org.mockito.Mockito.doAnswer(inv -> reactions.remove(inv.<Reaction>getArgument(0)))
                .when(reactionRepo).delete(any(Reaction.class));

        when(statusRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusRepo.save(any(Status.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User user(long id, String username, String name, String surname, String password) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setName(name);
        u.setSurname(surname);
        u.setPassword(password);
        return u;
    }

    private static Message message(long id) {
        return switch ((int) id) {
            case 1 -> new Message(1L, 1L, 1L, LocalDateTime.parse("2021-07-07T22:00:00"), MessageType.TEXT, "hi", null, null, null);
            case 13 -> new Message(13L, 2L, 3L, LocalDateTime.parse("2021-07-08T10:00:00"), MessageType.TEXT, "hello", null, null, null);
            default -> null;
        };
    }

    private static Chat chat(long id) {
        Set<Long> memberIds = MEMBERS.get(id);
        if (memberIds == null) {
            return null;
        }
        return Chat.builder().id(id)
                .users(USERS.stream().filter(u -> memberIds.contains(u.getId())).toList())
                .messages(id == 1 ? List.of(message(1)) : id == 2 ? List.of(message(13)) : List.of())
                .status(memberIds.stream().map(uid -> Status.builder()
                        .id(new StatusEmbeddedId(uid, id)).build()).toList())
                .build();
    }

    static RequestSpecification anonymous() {
        return RestAssured.given();
    }

    static RequestSpecification as(String token) {
        return RestAssured.given().header("Authorization", token);
    }

    static RequestSpecification asJson(String token, String body) {
        return as(token).contentType(ContentType.JSON).body(body);
    }

    static String login(String username, String password) {
        return RestAssured.given().contentType(ContentType.JSON)
                .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .post("/login")
                .then().statusCode(200).extract().header("Authentication");
    }

    static String janek() {
        return login("janek007", "qwerty");
    }

    static String grace() {
        return login("grace01", "1234");
    }

    static String ewa() {
        return login("ewa-nowak", "abcd");
    }
}
