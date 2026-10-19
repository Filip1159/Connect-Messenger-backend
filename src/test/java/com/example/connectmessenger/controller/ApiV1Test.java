package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

/** The REST API lives under /api/v1 only. */
class ApiV1Test extends ControllerComponentTest {

    private static String v1Janek() {
        return anonymous().contentType(ContentType.JSON).body("{\"username\":\"janek007\",\"password\":\"qwerty\"}")
                .post("/api/v1/login").then().statusCode(200).header("Authentication", startsWith("Bearer "))
                .extract().header("Authentication");
    }

    @Test
    void signingInAndRegisteringAreOpenUnderV1() {
        v1Janek();
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(50L);
            return u;
        });
        anonymous().contentType(ContentType.JSON)
                .body("{\"username\":\"newbie\",\"name\":\"N\",\"surname\":\"B\",\"password\":\"correct horse 42\"}")
                .post("/api/v1/users").then().statusCode(201);
        anonymous().get("/api/v1/chats").then().statusCode(401);
    }

    @Test
    void chatsAreReadWithoutPuttingTheUserIdInThePath() {
        String token = v1Janek();
        as(token).get("/api/v1/chats").then().statusCode(200).body("id", hasSize(4));  // chats 1, 2, 4 and the group (not 3)
        as(token).queryParam("includeMessages", false).get("/api/v1/chats").then().statusCode(200).body("[0].messages", hasSize(0));
        as(token).get("/api/v1/chats/1").then().statusCode(200).body("id", equalTo(1));
        as(token).get("/api/v1/chats/3").then().statusCode(403);
    }

    @Test
    void messagesArePagedUnderTheirChat() {
        String token = janek();
        as(token).get("/api/v1/chats/1/messages").then().statusCode(200).body("size()", org.hamcrest.Matchers.greaterThan(0));
        as(token).get("/api/v1/chats/2/messages?after=0").then().statusCode(200);
        as(grace()).get("/api/v1/chats/1/messages").then().statusCode(403);
    }

    @Test
    void messagesAreCreatedReadEditedReactedToAndDeletedUnderMessages() {
        String token = janek();
        as(token).multiPart("chatId", 1).multiPart("type", "TEXT").multiPart("textContent", "v1 hello")
                .post("/api/v1/messages").then().statusCode(200).body("content", equalTo("v1 hello"));

        as(token).get("/api/v1/messages/1").then().statusCode(200).body("id", equalTo(1));
        asJson(token, "{\"textContent\":\"edited\"}").patch("/api/v1/messages/1").then().statusCode(200).body("content", equalTo("edited"));
        asJson(token, "{\"emoji\":\"👍\"}").put("/api/v1/messages/1/reaction").then().statusCode(200);
        as(token).delete("/api/v1/messages/1/reaction").then().statusCode(200);
        as(token).delete("/api/v1/messages/1").then().statusCode(200);
        as(grace()).get("/api/v1/messages/1").then().statusCode(403);
    }

    @Test
    void groupAndSettingsEndpointsAreUnderChatsAndId() {
        String token = janek();
        asJson(token, "{\"archived\":true}").patch("/api/v1/chats/1/settings").then().statusCode(200);
        asJson(token, "{\"name\":\"Renamed\"}").patch("/api/v1/chats/" + GROUP_ID).then().statusCode(200).body("name", equalTo("Renamed"));
        as(token).put("/api/v1/chats/" + GROUP_ID + "/admins/2").then().statusCode(200);
        as(token).delete("/api/v1/chats/" + GROUP_ID + "/admins/2").then().statusCode(200);
        asJson(token, "{\"userIds\":[4]}").post("/api/v1/chats/" + GROUP_ID + "/members").then().statusCode(200);
        as(token).delete("/api/v1/chats/" + GROUP_ID + "/members/3").then().statusCode(200);
    }

    @Test
    void usersAreSearchedWithAQueryParameter() {
        String token = v1Janek();
        as(token).queryParam("query", "ewa").get("/api/v1/users").then().statusCode(200).body("username", org.hamcrest.Matchers.hasItem("ewa-nowak"));
        as(token).queryParam("query", "e").get("/api/v1/users").then().statusCode(200).body("size()", equalTo(0));
        as(token).get("/api/v1/users/me").then().statusCode(200).body("username", equalTo("janek007"));
        as(token).get("/api/v1/users/blocked").then().statusCode(200);
    }

    @Test
    void statusPresenceAndFilesAreVersionedToo() {
        String token = janek();
        asJson(token, "{\"id\":{\"chatId\":1},\"messageId\":1}").put("/api/v1/status").then().statusCode(200);
        as(token).get("/api/v1/presence").then().statusCode(200);
        as(token).get("/api/v1/messages/files/nothing-here.png").then().statusCode(404);
    }

    @Test
    void theFormerUnversionedPathsAreGone() {
        String token = janek();
        for (String path : new String[]{"/chats/1", "/chats/chat/1", "/chats/chat/1/messages", "/message/1", "/user/ewa",
                "/users/me", "/presence", "/message/file/x.png", "/message/file/x.png/raw"}) {
            as(token).get(path).then().statusCode(404);
        }
        as(token).put("/status").then().statusCode(404);
        as(token).post("/chats").then().statusCode(404);
        anonymous().contentType(ContentType.JSON).body("{\"username\":\"janek007\",\"password\":\"qwerty\"}")
                .post("/login").then().statusCode(401);
        anonymous().contentType(ContentType.JSON).body("{}").post("/users").then().statusCode(401);
    }
}
