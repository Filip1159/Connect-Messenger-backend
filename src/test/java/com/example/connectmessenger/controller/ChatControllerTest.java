package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Chat;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

class ChatControllerTest extends ControllerComponentTest {

    @Test
    void userSeesOnlyOwnChatsWithLazyCollectionsSerialized() {
        as(janek()).get("/chats/1")
                .then().statusCode(200)
                .body("id", hasItems(1, 2, 4))
                .body("id", not(hasItem(3)))
                .body("[0].users", notNullValue())
                .body("[0].messages", notNullValue())
                .body("[0].status", notNullValue());
    }

    @Test
    void chatListOfAnotherUserIsForbidden() {
        as(janek()).get("/chats/2").then().statusCode(403);
        as(janek()).get("/chats/4").then().statusCode(403);
    }

    @Test
    void singleChatCanBeReadOnlyByItsMembers() {
        as(janek()).get("/chats/chat/1").then().statusCode(200).body("id", equalTo(1))
                .body("users.id", containsInAnyOrder(1, 2));
        as(janek()).get("/chats/chat/3").then().statusCode(403);
        as(janek()).get("/chats/chat/999").then().statusCode(403);
    }

    @Test
    void chatCanBeCreatedAndReadBack() {
        String token = janek();

        int id = asJson(token, "{\"userIds\":[1,3]}").post("/chats")
                .then().statusCode(200)
                .body("id", equalTo(5))
                .body("users.size()", equalTo(2))
                .extract().path("id");

        assertThat(id).isEqualTo(5);
        verify(chatRepo).save(any(Chat.class));
        verify(statusRepo).saveAll(argThat(statuses -> ((Collection<?>) statuses).size() == 2));
    }

    @Test
    void chatMustIncludeTheCaller() {
        asJson(janek(), "{\"userIds\":[2,3]}").post("/chats").then().statusCode(403);
        asJson(janek(), "{}").post("/chats").then().statusCode(403);

        verify(chatRepo, never()).save(any());
    }

    @Test
    void invalidChatRequestsAreRejected() {
        asJson(janek(), "{\"userIds\":[1]}").post("/chats").then().statusCode(400);
        asJson(janek(), "{\"userIds\":[1,1]}").post("/chats").then().statusCode(400);
        asJson(janek(), "{\"userIds\":[1,999]}").post("/chats").then().statusCode(404);
    }
}
