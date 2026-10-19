package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.UserBlock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Group chats, one chat per pair, personal chat settings and blocking. */
class GroupChatControllerTest extends ControllerComponentTest {

    private static UserBlock block(long blocker, long blocked) {
        return UserBlock.builder().blockerId(blocker).blockedId(blocked).build();
    }

    @SuppressWarnings("unchecked")
    private List<Status> savedStatuses() {
        ArgumentCaptor<Collection<Status>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(statusRepo).saveAll(captor.capture());
        return List.copyOf(captor.getValue());
    }

    // ---- 3.4 one chat per pair

    @Test
    void creatingAChatThatAlreadyExistsReturnsTheExistingOne() {
        asJson(janek(), "{\"userIds\":[1,3]}").post("/chats")
                .then().statusCode(200).body("id", equalTo(2)).body("group", is(false));

        verify(chatRepo, never()).save(any());
    }

    // ---- 3.3 groups

    @Test
    void aNameMakesAGroupAndTheCreatorIsItsAdmin() {
        asJson(janek(), "{\"userIds\":[1,2],\"name\":\"  Duo \"}").post("/chats")
                .then().statusCode(200)
                .body("group", is(true)).body("name", equalTo("Duo")).body("adminIds", contains(1));

        var chat = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepo).save(chat.capture());
        assertThat(chat.getValue().isGroupChat()).isTrue();
        assertThat(savedStatuses()).extracting(s -> s.getId().getUserId(), Status::isGroupAdmin)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, true), org.assertj.core.groups.Tuple.tuple(2L, false));
    }

    @Test
    void moreThanTwoUsersMakeAGroupWithoutAName() {
        asJson(janek(), "{\"userIds\":[1,2,3]}").post("/chats")
                .then().statusCode(200).body("group", is(true)).body("users.size()", equalTo(3));
    }

    @Test
    void groupNamesAreLimited() {
        asJson(janek(), "{\"userIds\":[1,2],\"name\":\"" + "x".repeat(61) + "\"}").post("/chats").then().statusCode(400);
        verify(chatRepo, never()).save(any());
    }

    @Test
    void membersCanReadTheGroupWithItsAdmins() {
        as(ewa()).get("/chats/chat/" + GROUP_ID).then().statusCode(200)
                .body("name", equalTo("Team")).body("group", is(true)).body("adminIds", contains(1));
        as(grace()).get("/chats/chat/" + GROUP_ID).then().statusCode(403);
    }

    @Test
    void onlyAnAdminRenamesAGroup() {
        asJson(janek(), "{\"name\":\" New name \"}").patch("/chats/chat/" + GROUP_ID).then().statusCode(200);
        var chat = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepo).save(chat.capture());
        assertThat(chat.getValue().getName()).isEqualTo("New name");

        asJson(ewa(), "{\"name\":\"Mine\"}").patch("/chats/chat/" + GROUP_ID).then().statusCode(403);
        asJson(janek(), "{\"name\":\"  \"}").patch("/chats/chat/" + GROUP_ID).then().statusCode(400);
        asJson(janek(), "{\"name\":\"Mine\"}").patch("/chats/chat/1").then().statusCode(400);  // 1:1 chats have no name
    }

    @Test
    void anAdminAddsMembersAndTheyStartWithNothingUnread() {
        when(messageRepo.findFirstByChatIdOrderByIdDesc(GROUP_ID)).thenReturn(Optional.of(
                new com.example.connectmessenger.model.Message(77L, GROUP_ID, 1L, null, null, "x", null, null, null)));

        asJson(janek(), "{\"userIds\":[4]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(200);

        assertThat(savedStatuses()).singleElement().satisfies(s -> {
            assertThat(s.getId().getUserId()).isEqualTo(4L);
            assertThat(s.getId().getChatId()).isEqualTo(GROUP_ID);
            assertThat(s.getMessageId()).isEqualTo(77L);
            assertThat(s.isGroupAdmin()).isFalse();
        });
    }

    @Test
    void addingMembersIsRestricted() {
        asJson(ewa(), "{\"userIds\":[4]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(403);
        asJson(janek(), "{\"userIds\":[2]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(409);
        asJson(janek(), "{\"userIds\":[999]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(404);
        asJson(janek(), "{\"userIds\":[]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(400);
        asJson(janek(), "{\"userIds\":[4]}").post("/chats/chat/1/members").then().statusCode(400);  // 1:1
        asJson(grace(), "{\"userIds\":[4]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(403);

        when(userBlockRepo.findByBlockerIdAndBlockedId(4L, 1L)).thenReturn(Optional.of(block(4, 1)));
        asJson(janek(), "{\"userIds\":[4]}").post("/chats/chat/" + GROUP_ID + "/members").then().statusCode(403);

        verify(statusRepo, never()).saveAll(any());
    }

    @Test
    void anAdminRemovesAMember() {
        as(janek()).delete("/chats/chat/" + GROUP_ID + "/members/3").then().statusCode(200);

        var removed = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).delete(removed.capture());
        assertThat(removed.getValue().getId().getUserId()).isEqualTo(3L);
    }

    @Test
    void aMemberCanLeaveButNotRemoveOthers() {
        as(ewa()).delete("/chats/chat/" + GROUP_ID + "/members/3").then().statusCode(403);
        verify(statusRepo, never()).delete(any());

        as(ewa()).delete("/chats/chat/" + GROUP_ID + "/members/2").then().statusCode(200);
        var left = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).delete(left.capture());
        assertThat(left.getValue().getId().getUserId()).isEqualTo(2L);
    }

    @Test
    void whenTheLastAdminLeavesAnotherMemberTakesOver() {
        as(janek()).delete("/chats/chat/" + GROUP_ID + "/members/1").then().statusCode(200);

        var promoted = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).save(promoted.capture());
        assertThat(promoted.getValue().getId().getUserId()).isEqualTo(2L);
        assertThat(promoted.getValue().isGroupAdmin()).isTrue();
    }

    @Test
    void membersOfOneToOneChatsCannotLeaveOrChange() {
        as(janek()).delete("/chats/chat/1/members/2").then().statusCode(400);
        as(janek()).delete("/chats/chat/" + GROUP_ID + "/members/4").then().statusCode(404);
    }

    @Test
    void adminsAreManagedByAdminsAndOneAlwaysRemains() {
        as(janek()).put("/chats/chat/" + GROUP_ID + "/admins/2").then().statusCode(200);
        var promoted = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).save(promoted.capture());
        assertThat(promoted.getValue().getId().getUserId()).isEqualTo(2L);
        assertThat(promoted.getValue().isGroupAdmin()).isTrue();

        as(ewa()).put("/chats/chat/" + GROUP_ID + "/admins/3").then().statusCode(403);
        as(janek()).delete("/chats/chat/" + GROUP_ID + "/admins/1").then().statusCode(409);  // the only admin
        as(janek()).put("/chats/chat/" + GROUP_ID + "/admins/4").then().statusCode(404);
    }

    // ---- 3.5 archive and mute

    @Test
    void archiveAndMuteArePersonalSettingsOfAMember() {
        asJson(janek(), "{\"archived\":true,\"muted\":true}").patch("/chats/chat/1/settings").then().statusCode(200);

        var saved = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).save(saved.capture());
        assertThat(saved.getValue().getId().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().isArchived()).isTrue();
        assertThat(saved.getValue().isMuted()).isTrue();
    }

    @Test
    void settingsCanBeChangedOneAtATimeAndOnlyByMembers() {
        asJson(janek(), "{\"muted\":true}").patch("/chats/chat/1/settings").then().statusCode(200);
        var saved = ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).save(saved.capture());
        assertThat(saved.getValue().isArchived()).isFalse();
        assertThat(saved.getValue().isMuted()).isTrue();

        asJson(grace(), "{\"muted\":true}").patch("/chats/chat/1/settings").then().statusCode(403);
    }

    @Test
    void theChatListCarriesTheGroupAndTheReadersFlags() {
        as(janek()).get("/chats/1").then().statusCode(200)
                .body("id", hasItem((int) GROUP_ID))
                .body("find { it.id == " + GROUP_ID + " }.name", equalTo("Team"))
                .body("find { it.id == " + GROUP_ID + " }.group", is(true))
                .body("find { it.id == 1 }.group", is(false))
                .body("archived", org.hamcrest.Matchers.everyItem(is(false)))
                .body("muted", org.hamcrest.Matchers.everyItem(is(false)));
    }

    // ---- 3.6 blocking

    @Test
    void youCannotStartAChatWithSomeoneWhoBlockedYouOrWhomYouBlocked() {
        when(userBlockRepo.findByBlockerIdAndBlockedId(2L, 4L)).thenReturn(Optional.of(block(2, 4)));
        asJson(grace(), "{\"userIds\":[4,2]}").post("/chats").then().statusCode(403);

        when(userBlockRepo.findByBlockerIdAndBlockedId(4L, 3L)).thenReturn(Optional.of(block(4, 3)));
        asJson(grace(), "{\"userIds\":[4,2,3],\"name\":\"G\"}").post("/chats").then().statusCode(403);

        verify(chatRepo, never()).save(any());
    }

    @Test
    void messagesToABlockingUserAreRefusedInAOneToOneChat() {
        when(userBlockRepo.findByBlockerIdAndBlockedId(2L, 1L)).thenReturn(Optional.of(block(2, 1)));

        as(janek()).multiPart("chatId", 1).multiPart("type", "TEXT").multiPart("textContent", "hi")
                .post("/message").then().statusCode(403);
        // and the other way round
        as(ewa()).multiPart("chatId", 1).multiPart("type", "TEXT").multiPart("textContent", "hi")
                .post("/message").then().statusCode(403);
        // other chats are not affected, nor are groups
        as(janek()).multiPart("chatId", 2).multiPart("type", "TEXT").multiPart("textContent", "hi")
                .post("/message").then().statusCode(200);
        as(janek()).multiPart("chatId", GROUP_ID).multiPart("type", "TEXT").multiPart("textContent", "hi")
                .post("/message").then().statusCode(200);
    }
}
