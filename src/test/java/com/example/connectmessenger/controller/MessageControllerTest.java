package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        as(janek()).get("/message/1").then().statusCode(200).body("id", equalTo(1));
        as(grace()).get("/message/1").then().statusCode(403);
        as(janek()).get("/message/99999").then().statusCode(404);
    }

    @Test
    void textMessageCanBePostedAsYourselfIntoYourChat() {
        textMessage(janek(), 1, 1, "hello").post("/message")
                .then().statusCode(200)
                .body("content", equalTo("hello"))
                .body("userId", equalTo(1))
                .body("chatId", equalTo(1));

        verify(messageRepo).save(any(Message.class));
    }

    @Test
    void messageCannotBePostedAsSomeoneElseOrIntoForeignChat() {
        textMessage(janek(), 2, 1, "spoofed").post("/message").then().statusCode(403);
        textMessage(janek(), 1, 3, "foreign chat").post("/message").then().statusCode(403);
        textMessage(grace(), 4, 1, "foreign chat").post("/message").then().statusCode(403);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void invalidMessagesAreRejected() {
        textMessage(janek(), 1, 1, "   ").post("/message").then().statusCode(400);
        textMessage(janek(), 1, 1, "x".repeat(1001)).post("/message").then().statusCode(400);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void imageUploadStoresTheFileUnderAGeneratedName() {
        fileMessage(janek(), PNG).post("/message")
                .then().statusCode(200)
                .body("content", startsWith("1_"))
                .body("content", endsWith(".png"));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void nonImageUploadIsRejectedAndNothingIsStored() {
        fileMessage(janek(), "<script>".getBytes(StandardCharsets.UTF_8)).post("/message").then().statusCode(400);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void fileCanBeDownloadedOnlyByMembersOfItsChat() {
        String name = fileMessage(janek(), PNG).post("/message").then().statusCode(200).extract().path("content");
        when(messageRepo.findByTypeAndContent(MessageType.FILE, name))
                .thenReturn(Optional.of(new Message(100L, 1L, 1L, LocalDateTime.now(), MessageType.FILE, name)));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PNG));

        String body = as(janek()).get("/message/file/" + name).then().statusCode(200).extract().asString();

        assertThat(body).isEqualTo(Base64.getEncoder().encodeToString(PNG));
        as(grace()).get("/message/file/" + name).then().statusCode(403);
    }

    @Test
    void arbitraryBucketKeysCannotBeDownloaded() {
        as(janek()).get("/message/file/secret-report.pdf").then().statusCode(404);
        as(janek()).get("/message/file/1").then().statusCode(404);

        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }
}
