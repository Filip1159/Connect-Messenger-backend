package com.example.connectmessenger.persistence;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.Reaction;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.UserBlock;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.ReactionRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.repo.UserBlockRepo;
import com.example.connectmessenger.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceIT extends MySqlIntegrationTest {
    @Autowired UserRepo users;
    @Autowired ChatRepo chats;
    @Autowired MessageRepo messages;
    @Autowired StatusRepo statuses;
    @Autowired ReactionRepo reactions;
    @Autowired UserBlockRepo blocks;
    @Autowired TransactionTemplate tx;

    private User ewa;
    private User jan;
    private Chat direct;

    private User user(String username) {
        User u = new User();
        u.setUsername(username);
        u.setName(username);
        u.setSurname("Test");
        u.setPassword("{noop}x");
        return users.save(u);
    }

    private void join(User u, Chat c, boolean admin) {
        statuses.save(Status.builder().id(new StatusEmbeddedId(u.getId(), c.getId())).time(LocalDateTime.now()).groupAdmin(admin).build());
    }

    private Message post(Chat c, User u, String text) {
        return messages.save(Message.builder().chatId(c.getId()).userId(u.getId()).time(LocalDateTime.now())
                .type(MessageType.TEXT).content(text).build());
    }

    @BeforeEach
    void data() {
        String suffix = String.valueOf(System.nanoTime());
        ewa = user("ewa" + suffix);
        jan = user("jan" + suffix);
        direct = chats.save(Chat.builder().build());
        join(ewa, direct, false);
        join(jan, direct, false);
    }

    @Test
    void aNewChatIsADirectChatAndMembershipFlagsDefaultToFalse() {
        Chat reloaded = chats.findById(direct.getId()).orElseThrow();
        assertThat(reloaded.isGroupChat()).isFalse();
        Status s = statuses.findById(new StatusEmbeddedId(ewa.getId(), direct.getId())).orElseThrow();
        assertThat(s.isGroupAdmin()).isFalse();
        assertThat(s.isArchived()).isFalse();
        assertThat(s.isMuted()).isFalse();
    }

    @Test
    void findsTheDirectChatOfTwoUsersButNotAGroup() {
        Chat group = chats.save(Chat.builder().name("Team").groupChat(true).build());
        join(ewa, group, true);
        join(jan, group, false);

        assertThat(chats.findDirectChats(ewa.getId(), jan.getId())).extracting(Chat::getId).containsExactly(direct.getId());
        assertThat(chats.sharesChat(ewa.getId(), jan.getId())).isTrue();
        assertThat(chats.existsByIdAndUsers_Id(group.getId(), jan.getId())).isTrue();
        assertThat(chats.findByUsers_Id(ewa.getId())).extracting(Chat::getId).contains(direct.getId(), group.getId());
        assertThat(statuses.findByIdChatId(group.getId())).hasSize(2).filteredOn(Status::isGroupAdmin).hasSize(1);
    }

    @Test
    void pagesMessagesNewestFirstAndCatchesUpOldestFirst() {
        List<Message> all = java.util.stream.IntStream.rangeClosed(1, 7).mapToObj(i -> post(direct, ewa, "m" + i)).toList();
        Long last = all.getLast().getId();

        List<Message> firstPage = messages.findByChatIdAndIdLessThanOrderByIdDesc(direct.getId(), last + 1, Limit.of(3));
        assertThat(firstPage).extracting(Message::getContent).containsExactly("m7", "m6", "m5");

        Long oldestSeen = firstPage.getLast().getId();
        assertThat(messages.findByChatIdAndIdLessThanOrderByIdDesc(direct.getId(), oldestSeen, Limit.of(10)))
                .extracting(Message::getContent).containsExactly("m4", "m3", "m2", "m1");

        assertThat(messages.findByChatIdAndIdGreaterThanOrderByIdAsc(direct.getId(), all.get(4).getId(), Limit.of(10)))
                .extracting(Message::getContent).containsExactly("m6", "m7");
        assertThat(messages.findFirstByChatIdOrderByIdDesc(direct.getId())).get().extracting(Message::getContent).isEqualTo("m7");
    }

    @Test
    void countsOnlyUnreadMessagesOfOthers() {
        Message first = post(direct, ewa, "a");
        post(direct, jan, "b");
        post(direct, jan, "c");
        post(direct, ewa, "mine");

        assertThat(messages.countByChatIdAndIdGreaterThanAndUserIdNot(direct.getId(), first.getId(), ewa.getId())).isEqualTo(2);
    }

    @Test
    void searchesLiveTextMessagesCaseInsensitivelyAndTreatsWildcardsAsText() {
        post(direct, ewa, "Hello World");
        post(direct, ewa, "100% sure");
        Message deleted = post(direct, ewa, "hello deleted");
        deleted.setDeletedAt(LocalDateTime.now());
        messages.save(deleted);
        Long before = Long.MAX_VALUE;

        assertThat(messages.findByChatIdAndTypeAndDeletedAtIsNullAndContentContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                direct.getId(), MessageType.TEXT, "HELLO", before, Limit.of(10)))
                .extracting(Message::getContent).containsExactly("Hello World");
        assertThat(messages.findByChatIdAndTypeAndDeletedAtIsNullAndContentContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                direct.getId(), MessageType.TEXT, "%", before, Limit.of(10)))
                .extracting(Message::getContent).containsExactly("100% sure");
    }

    @Test
    void storesTheTypeOfFileMessagesAndLongTexts() {
        Message file = messages.save(Message.builder().chatId(direct.getId()).userId(ewa.getId()).time(LocalDateTime.now())
                .type(MessageType.FILE).content("img.png").build());
        Message longText = post(direct, ewa, "x".repeat(1000));

        assertThat(messages.findById(file.getId())).get().extracting(Message::getType).isEqualTo(MessageType.FILE);
        assertThat(messages.findById(longText.getId())).get().extracting(m -> m.getContent().length()).isEqualTo(1000);
    }

    @Test
    void usernamesAreUniqueAndSearchableIgnoringCase() {
        assertThatThrownBy(() -> user(ewa.getUsername())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.existsByUsernameIgnoreCase(ewa.getUsername().toUpperCase())).isTrue();
        assertThat(users.getUserByUsername(jan.getUsername())).isPresent();
    }

    @Test
    void oneReactionPerUserAndMessage() {
        Message m = post(direct, ewa, "react to me");
        reactions.save(Reaction.builder().messageId(m.getId()).userId(jan.getId()).emoji("👍").build());

        assertThatThrownBy(() -> reactions.saveAndFlush(Reaction.builder().messageId(m.getId()).userId(jan.getId()).emoji("😂").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reactions.findByMessageIdIn(List.of(m.getId()))).extracting(Reaction::getEmoji).containsExactly("👍");
        assertThat(reactions.findByMessageIdAndUserId(m.getId(), jan.getId())).isPresent();
    }

    @Test
    void aUserCanBlockAnotherOnlyOnce() {
        blocks.save(UserBlock.builder().blockerId(ewa.getId()).blockedId(jan.getId()).build());

        assertThatThrownBy(() -> blocks.saveAndFlush(UserBlock.builder().blockerId(ewa.getId()).blockedId(jan.getId()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(blocks.findByBlockerId(ewa.getId())).hasSize(1);
        assertThat(blocks.findByBlockedId(jan.getId())).hasSize(1);
        assertThat(blocks.findByBlockerIdAndBlockedId(jan.getId(), ewa.getId())).isEmpty();
    }

    @Test
    void emojiSurviveARoundTripThroughMysql() {
        Message m = post(direct, ewa, "😀 zażółć gęślą jaźń");
        assertThat(messages.findById(m.getId())).get().extracting(Message::getContent).isEqualTo("😀 zażółć gęślą jaźń");
    }


    @Test
    void aDatabaseBuiltByTheFormerDdlAutoIsAdoptedWithoutRunningTheBaseline() throws Exception {
        // an existing database: tables, but no Flyway history; the baseline must be recorded, not executed (it would fail)
        try (var c = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())) {
            c.createStatement().execute("create database legacy");
            c.createStatement().execute("create table legacy.users (id bigint primary key)");
        }
        String url = MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/legacy");
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(url, "root", MYSQL.getPassword())
                .baselineOnMigrate(true).baselineVersion("1").load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }
}
