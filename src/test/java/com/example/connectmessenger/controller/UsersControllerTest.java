package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.UserBlock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Registration, the profile of the signed-in user, avatars and blocking. */
class UsersControllerTest extends ControllerComponentTest {

    private static String registration(String username, String password) {
        return "{\"username\":\"%s\",\"name\":\"Anna\",\"surname\":\"Lis\",\"password\":\"%s\"}".formatted(username, password);
    }

    private User saved() {
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        return captor.getValue();
    }

    // ---- 3.1 registration

    @Test
    void anyoneCanRegisterAndThenSignIn() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(50L);
            return u;
        });
        anonymous().contentType("application/json").body(registration("anna.lis", "secret123")).post("/api/v1/users")
                .then().statusCode(201)
                .body("id", equalTo(50)).body("username", equalTo("anna.lis")).body("name", equalTo("Anna"))
                .body("password", nullValue());

        User user = saved();
        assertThat(user.getPassword()).startsWith("{bcrypt}");
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("secret123", user.getPassword())).isTrue();
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        when(userRepo.existsByUsernameIgnoreCase("EWA-nowak")).thenReturn(true);

        anonymous().contentType("application/json").body(registration("EWA-nowak", "secret123")).post("/api/v1/users")
                .then().statusCode(409).body("message", equalTo("This username is taken"));
        verify(userRepo, never()).save(any());
    }

    @Test
    void registrationIsValidated() {
        for (String body : List.of(
                registration("ab", "secret123"),  // too short
                registration("has space", "secret123"),
                registration("anna.lis", "short1"),
                registration("anna.lis", "onlyletters"),
                registration("anna.lis", "12345678"),
                registration("anna.lis", "x1".repeat(40)),  // more than bcrypt reads
                "{\"username\":\"anna.lis\",\"name\":\" \",\"surname\":\"Lis\",\"password\":\"secret123\"}",
                "{\"username\":\"anna.lis\",\"name\":\"Anna\",\"password\":\"secret123\"}",
                "{}")) {
            anonymous().contentType("application/json").body(body).post("/api/v1/users").then().statusCode(400);
        }
        verify(userRepo, never()).save(any());
    }

    @Test
    void otherUserEndpointsStillNeedASession() {
        anonymous().get("/api/v1/users/blocked").then().statusCode(401);
        anonymous().contentType("application/json").body("{}").patch("/api/v1/users/me").then().statusCode(401);
    }

    // ---- 3.2 profile

    @Test
    void theProfileCanBeRead() {
        as(janek()).get("/api/v1/users/me").then().statusCode(200)
                .body("id", equalTo(1)).body("username", equalTo("janek007")).body("password", nullValue());
    }

    @Test
    void theProfileNameCanBeChanged() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        asJson(janek(), "{\"name\":\" Janusz \",\"surname\":\"Nowak\"}").patch("/api/v1/users/me")
                .then().statusCode(200).body("id", equalTo(1)).body("name", equalTo("Janusz")).body("surname", equalTo("Nowak"));
        asJson(janek(), "{\"name\":\"\",\"surname\":\"Nowak\"}").patch("/api/v1/users/me").then().statusCode(400);
    }

    @Test
    void thePasswordChangeNeedsTheCurrentPassword() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        asJson(janek(), "{\"currentPassword\":\"wrong\",\"newPassword\":\"better123\"}").put("/api/v1/users/me/password")
                .then().statusCode(403);
        asJson(janek(), "{\"currentPassword\":\"qwerty\",\"newPassword\":\"weak\"}").put("/api/v1/users/me/password")
                .then().statusCode(400);
        verify(userRepo, never()).save(any());

        asJson(janek(), "{\"currentPassword\":\"qwerty\",\"newPassword\":\"better123\"}").put("/api/v1/users/me/password")
                .then().statusCode(204);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("better123", saved().getPassword())).isTrue();
    }

    @Test
    void anAvatarCanBeUploadedAndServed() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        {
            as(janek()).multiPart("file", "me.png", PNG, "image/png").put("/api/v1/users/me/avatar")
                    .then().statusCode(200).body("avatarKey", startsWith("avatar_1_"));
            verify(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

            var stored = new User();  // what the database holds after the upload
            stored.setId(1L);
            stored.setAvatarKey(saved().getAvatarKey());
            when(userRepo.findById(1L)).thenReturn(Optional.of(stored));
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PNG));
            as(grace()).get("/api/v1/users/1/avatar").then().statusCode(200).contentType("image/png");
        }
    }

    @Test
    void avatarsMustBeImages() {
        as(janek()).multiPart("file", "me.exe", new byte[]{1, 2, 3, 4}, "image/png").put("/api/v1/users/me/avatar")
                .then().statusCode(400);
        as(janek()).get("/api/v1/users/2/avatar").then().statusCode(404);  // no avatar uploaded
        as(janek()).get("/api/v1/users/999/avatar").then().statusCode(404);
    }

    @Test
    void anAvatarCanBeRemoved() {
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        as(janek()).delete("/api/v1/users/me/avatar").then().statusCode(200).body("avatarKey", nullValue());
        assertThat(saved().getAvatarKey()).isNull();
    }

    // ---- 3.6 blocking

    @Test
    void aUserCanBeBlockedAndUnblocked() {
        as(janek()).put("/api/v1/users/2/block").then().statusCode(204);
        var block = ArgumentCaptor.forClass(UserBlock.class);
        verify(userBlockRepo).save(block.capture());
        assertThat(block.getValue().getBlockerId()).isEqualTo(1L);
        assertThat(block.getValue().getBlockedId()).isEqualTo(2L);

        var existing = UserBlock.builder().id(9L).blockerId(1L).blockedId(2L).build();
        when(userBlockRepo.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(existing));
        as(janek()).put("/api/v1/users/2/block").then().statusCode(204);  // idempotent
        verify(userBlockRepo).save(any());  // still only the first one

        as(janek()).delete("/api/v1/users/2/block").then().statusCode(204);
        verify(userBlockRepo).delete(existing);
    }

    @Test
    void blockingNeedsAnotherExistingUser() {
        as(janek()).put("/api/v1/users/1/block").then().statusCode(400);
        as(janek()).put("/api/v1/users/999/block").then().statusCode(404);
        verify(userBlockRepo, never()).save(any());
    }

    @Test
    void theBlockedListShowsOnlyMyBlocks() {
        when(userBlockRepo.findByBlockerId(1L)).thenReturn(List.of(UserBlock.builder().blockerId(1L).blockedId(3L).build()));

        as(janek()).get("/api/v1/users/blocked").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].username", equalTo("mrsmith")).body("[0].password", nullValue());
    }

    @Test
    void usersWhoBlockedMeDoNotShowInTheSearch() {
        when(userBlockRepo.findByBlockedId(1L)).thenReturn(List.of(UserBlock.builder().blockerId(2L).blockedId(1L).build()));

        as(janek()).get("/api/v1/users?query=ewa").then().statusCode(200).body("$", hasSize(0));
    }

    // ---- CORS: the browser app lives on another origin, so every method it uses must be allowed

    @Test
    void thePreflightAllowsEveryMethodTheAppUses() {
        for (String method : List.of("PATCH", "DELETE", "PUT", "POST")) {
            anonymous().header("Origin", "http://localhost:3000").header("Access-Control-Request-Method", method)
                    .options("/api/v1/messages/1")
                    .then().statusCode(200).header("Access-Control-Allow-Methods", containsString(method));
        }
    }
}
