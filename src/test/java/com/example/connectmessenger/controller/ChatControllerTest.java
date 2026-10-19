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
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

class ChatControllerTest extends ControllerComponentTest {

    @Test
    void userSeesOnlyOwnChatsWithLazyCollectionsSerialized() {
        as(janek()).get("/api/v1/chats")
                .then().statusCode(200)
                .body("id", hasItems(1, 2, 4))
                .body("id", not(hasItem(3)))
                .body("[0].users", notNullValue())
                .body("[0].messages", notNullValue())
                .body("[0].status", notNullValue());
    }

    @Test
    void chatListReportsUnreadMessages() {
        as(janek()).get("/api/v1/chats").then().statusCode(200).body("unreadCount", org.hamcrest.Matchers.everyItem(equalTo(3)));
        as(janek()).get("/api/v1/chats?includeMessages=false").then().statusCode(200)
                .body("unreadCount", org.hamcrest.Matchers.everyItem(equalTo(3)));
    }

    @Test
    void chatListCanBeLoadedWithoutMessages() {
        as(janek()).get("/api/v1/chats?includeMessages=false")
                .then().statusCode(200)
                .body("id", hasItems(1, 2, 4))
                .body("[0].messages", org.hamcrest.Matchers.empty())
                .body("[0].users", notNullValue());
    }

    @Test
    void singleChatCanBeReadOnlyByItsMembers() {
        as(janek()).get("/api/v1/chats/1").then().statusCode(200).body("id", equalTo(1))
                .body("users.id", containsInAnyOrder(1, 2));
        as(janek()).get("/api/v1/chats/3").then().statusCode(403);
        as(janek()).get("/api/v1/chats/999").then().statusCode(403);
    }

    @Test
    void chatCanBeCreatedAndReadBack() {
        String token = grace();

        int id = asJson(token, "{\"userIds\":[4,2]}").post("/api/v1/chats")
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
        asJson(janek(), "{\"userIds\":[2,3]}").post("/api/v1/chats").then().statusCode(403);
        asJson(janek(), "{}").post("/api/v1/chats").then().statusCode(403);

        verify(chatRepo, never()).save(any());
    }

    @Test
    void invalidChatRequestsAreRejected() {
        asJson(janek(), "{\"userIds\":[1]}").post("/api/v1/chats").then().statusCode(400);
        asJson(janek(), "{\"userIds\":[1,1]}").post("/api/v1/chats").then().statusCode(400);
        asJson(janek(), "{\"userIds\":[1,999]}").post("/api/v1/chats").then().statusCode(404);
    }

    @Test
    void errorsHaveOneJsonShape() {
        asJson(janek(), "{\"userIds\":[1,999]}").post("/api/v1/chats")
                .then().statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", equalTo("User not found: 999"));
    }

    @Test
    void unexpectedFailuresAreReportedGenericallyWithoutLeakingInternals() {
        when(userRepo.findById(3L)).thenThrow(new IllegalStateException("db password is hunter2"));

        String body = asJson(janek(), "{\"userIds\":[1,3]}").post("/api/v1/chats")
                .then().statusCode(500)
                .body("message", equalTo("Unexpected error"))
                .extract().asString();

        assertThat(body).doesNotContain("hunter2");
    }
}
