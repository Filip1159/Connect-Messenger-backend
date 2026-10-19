package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class MessageControllerTest extends ControllerComponentTest {

    private static RequestSpecification textMessage(String token, long userId, long chatId, String text) {
        return as(token).multiPart("userId", userId).multiPart("chatId", chatId)
                .multiPart("type", "TEXT").multiPart("textContent", text);
    }

    private static RequestSpecification fileMessage(String token, byte[] bytes) {
        return as(token).multiPart("userId", 1).multiPart("chatId", 1).multiPart("type", "FILE")
                .multiPart("fileContent", "holiday.exe", bytes, "image/png");
    }

    @Test
    void messageCanBeReadOnlyByChatMembers() {
        as(janek()).get("/api/v1/messages/1").then().statusCode(200).body("id", equalTo(1));
        as(grace()).get("/api/v1/messages/1").then().statusCode(403);
        as(janek()).get("/api/v1/messages/99999").then().statusCode(404);
    }

    @Test
    void messagesNewerThanAGivenOneCanBeLoadedOldestFirst() {
        as(janek()).get("/api/v1/chats/1/messages?after=117&size=10")
                .then().statusCode(200)
                .body("id", org.hamcrest.Matchers.contains(118, 119, 120));
        as(grace()).get("/api/v1/chats/1/messages?after=117").then().statusCode(403);
    }

    @Test
    void textMessageCanBePostedAsYourselfIntoYourChat() {
        textMessage(janek(), 1, 1, "hello").post("/api/v1/messages")
                .then().statusCode(200)
                .body("content", equalTo("hello"))
                .body("userId", equalTo(1))
                .body("chatId", equalTo(1));

        verify(messageRepo).save(any(Message.class));
    }

    @Test
    void senderIsTheAuthenticatedUserEvenWithoutUserId() {
        as(janek()).multiPart("chatId", 1).multiPart("type", "TEXT").multiPart("textContent", "hi")
                .post("/api/v1/messages").then().statusCode(200).body("userId", equalTo(1));
    }

    @Test
    void messageCannotBePostedAsSomeoneElseOrIntoForeignChat() {
        textMessage(janek(), 2, 1, "spoofed").post("/api/v1/messages").then().statusCode(403);
        textMessage(janek(), 1, 3, "foreign chat").post("/api/v1/messages").then().statusCode(403);
        textMessage(grace(), 4, 1, "foreign chat").post("/api/v1/messages").then().statusCode(403);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void invalidMessagesAreRejected() {
        textMessage(janek(), 1, 1, "   ").post("/api/v1/messages").then().statusCode(400);
        textMessage(janek(), 1, 1, "x".repeat(1001)).post("/api/v1/messages").then().statusCode(400);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void imageUploadStoresTheFileUnderAGeneratedName() {
        fileMessage(janek(), PNG).post("/api/v1/messages")
                .then().statusCode(200)
                .body("content", startsWith("1_"))
                .body("content", endsWith(".png"));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void nonImageUploadIsRejectedAndNothingIsStored() {
        fileMessage(janek(), "<script>".getBytes(StandardCharsets.UTF_8)).post("/api/v1/messages").then().statusCode(400);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void arbitraryBucketKeysCannotBeDownloaded() {
        as(janek()).get("/api/v1/messages/files/secret-report.pdf").then().statusCode(404);
        as(janek()).get("/api/v1/messages/files/1").then().statusCode(404);

        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void historyIsPagedNewestPageFirstAndOldestFirstWithinAPage() {
        as(janek()).get("/api/v1/chats/1/messages?size=3")
                .then().statusCode(200)
                .body("id", contains(118, 119, 120));
        as(janek()).get("/api/v1/chats/1/messages?size=3&before=118")
                .then().statusCode(200)
                .body("id", contains(115, 116, 117));
    }

    @Test
    void historyPageSizeIsCappedAndMembershipIsRequired() {
        as(janek()).get("/api/v1/chats/1/messages?size=100000").then().statusCode(200).body("size()", equalTo(100));
        as(janek()).get("/api/v1/chats/1/messages").then().statusCode(200).body("size()", equalTo(50));
        as(janek()).get("/api/v1/chats/3/messages").then().statusCode(403);
        anonymous().get("/api/v1/chats/1/messages").then().statusCode(401);
    }

    @Test
    void rawFileIsServedWithItsContentTypeToMembersOnly() {
        String name = fileMessage(janek(), PNG).post("/api/v1/messages").then().statusCode(200).extract().path("content");
        when(messageRepo.findByTypeAndContent(MessageType.FILE, name))
                .thenReturn(Optional.of(new Message(100L, 1L, 1L, LocalDateTime.now(), MessageType.FILE, name, null, null, null)));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PNG));

        byte[] bytes = as(janek()).get("/api/v1/messages/files/" + name)
                .then().statusCode(200).contentType("image/png").extract().asByteArray();

        assertThat(bytes).isEqualTo(PNG);
        as(grace()).get("/api/v1/messages/files/" + name).then().statusCode(403);
    }

    @Test
    void authorCanEditTheTextAndTheChangeIsMarked() {
        asJson(janek(), "{\"textContent\":\"hello again\"}").patch("/api/v1/messages/1")
                .then().statusCode(200)
                .body("content", equalTo("hello again"))
                .body("editedAt", org.hamcrest.Matchers.notNullValue())
                .body("deleted", equalTo(false));

        verify(messageRepo).save(any(Message.class));
    }

    @Test
    void messageCanBeEditedOnlyByItsAuthorWithValidText() {
        asJson(grace(), "{\"textContent\":\"x\"}").patch("/api/v1/messages/1").then().statusCode(403);
        asJson(ewa(), "{\"textContent\":\"x\"}").patch("/api/v1/messages/1").then().statusCode(403);
        asJson(janek(), "{\"textContent\":\"  \"}").patch("/api/v1/messages/1").then().statusCode(400);
        asJson(janek(), "{\"textContent\":\"" + "a".repeat(1001) + "\"}").patch("/api/v1/messages/1").then().statusCode(400);
        asJson(janek(), "{\"textContent\":\"x\"}").patch("/api/v1/messages/99999").then().statusCode(404);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void authorCanDeleteAMessageWhichThenHasNoContent() {
        as(janek()).delete("/api/v1/messages/1")
                .then().statusCode(200)
                .body("deleted", equalTo(true))
                .body("content", equalTo(""));

        as(grace()).delete("/api/v1/messages/1").then().statusCode(403);
        as(ewa()).delete("/api/v1/messages/1").then().statusCode(403);
    }

    @Test
    void deletedMessagesCannotBeEditedAndTheirFilesAreGone() {
        var deleted = new Message(100L, 1L, 1L, LocalDateTime.now(), MessageType.FILE, "1_a.png", null, LocalDateTime.now(), null);
        when(messageRepo.findById(100L)).thenReturn(Optional.of(deleted));
        when(messageRepo.findByTypeAndContent(MessageType.FILE, "1_a.png")).thenReturn(Optional.of(deleted));

        asJson(janek(), "{\"textContent\":\"x\"}").patch("/api/v1/messages/100").then().statusCode(409);
        as(janek()).get("/api/v1/messages/files/1_a.png").then().statusCode(404);
        as(janek()).get("/api/v1/messages/100").then().statusCode(200).body("deleted", equalTo(true)).body("content", equalTo(""));
    }

    @Test
    void textMessagesCanBeSearchedNewestFirst() {
        as(janek()).get("/api/v1/chats/1/messages?query=M11&size=3")
                .then().statusCode(200)
                .body("id", contains(119, 118, 117));
        as(janek()).get("/api/v1/chats/1/messages?query=m11&size=3&before=117")
                .then().statusCode(200)
                .body("id", contains(116, 115, 114));
    }

    @Test
    void searchNeedsAQueryAndMembership() {
        as(janek()).queryParam("query", "  ").get("/api/v1/chats/1/messages").then().statusCode(400);
        as(janek()).get("/api/v1/chats/1/messages?query=" + "a".repeat(101)).then().statusCode(400);
        as(grace()).get("/api/v1/chats/1/messages?query=m1").then().statusCode(403);
    }

    @Test
    void messageCanReplyToAMessageOfTheSameChat() {
        textMessage(janek(), 1, 1, "answer").multiPart("replyToId", 1).post("/api/v1/messages")
                .then().statusCode(200)
                .body("replyToId", equalTo(1));
    }

    @Test
    void replyTargetMustExistInTheSameChat() {
        textMessage(janek(), 1, 1, "wrong chat").multiPart("replyToId", 13).post("/api/v1/messages").then().statusCode(400);
        textMessage(janek(), 1, 1, "no such message").multiPart("replyToId", 99999).post("/api/v1/messages").then().statusCode(400);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void membersCanReactAndChangeOrRemoveTheirReaction() {
        asJson(ewa(), "{\"emoji\":\"👍\"}").put("/api/v1/messages/1/reaction")
                .then().statusCode(200)
                .body("reactions.emoji", contains("👍"))
                .body("reactions[0].userIds", contains(2));

        asJson(janek(), "{\"emoji\":\"👍\"}").put("/api/v1/messages/1/reaction")
                .then().statusCode(200)
                .body("reactions[0].userIds", contains(2, 1));

        asJson(ewa(), "{\"emoji\":\"❤️\"}").put("/api/v1/messages/1/reaction")
                .then().statusCode(200)
                .body("reactions.emoji", contains("❤️", "👍"));

        as(janek()).delete("/api/v1/messages/1/reaction")
                .then().statusCode(200)
                .body("reactions.emoji", contains("❤️"));
        as(janek()).delete("/api/v1/messages/1/reaction").then().statusCode(200);  // idempotent

        as(janek()).get("/api/v1/messages/1").then().body("reactions.emoji", contains("❤️"));
    }

    @Test
    void reactionsAreLimitedToMembersAndKnownEmojis() {
        asJson(grace(), "{\"emoji\":\"👍\"}").put("/api/v1/messages/1/reaction").then().statusCode(403);
        as(grace()).delete("/api/v1/messages/1/reaction").then().statusCode(403);
        asJson(janek(), "{\"emoji\":\"<script>\"}").put("/api/v1/messages/1/reaction").then().statusCode(400);
        asJson(janek(), "{}").put("/api/v1/messages/1/reaction").then().statusCode(400);
        asJson(janek(), "{\"emoji\":\"👍\"}").put("/api/v1/messages/99999/reaction").then().statusCode(404);

        assertThat(reactions).isEmpty();
    }

    @Test
    void historyCarriesTheReactions() {
        asJson(ewa(), "{\"emoji\":\"🙏\"}").put("/api/v1/messages/1/reaction").then().statusCode(200);

        as(janek()).get("/api/v1/chats/1/messages?after=0&size=2")
                .then().statusCode(200)
                .body("[0].reactions.emoji", contains("🙏"))
                .body("[1].reactions", org.hamcrest.Matchers.empty());
    }
}
