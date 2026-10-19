package com.example.connectmessenger.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

class AuthenticationControllerTest extends ControllerComponentTest {

    private static void loginExpecting401(String body) {
        anonymous().contentType(ContentType.JSON).body(body).post("/api/v1/login").then().statusCode(401);
    }

    @Test
    void loginReturnsABearerToken() {
        anonymous().contentType(ContentType.JSON).body("{\"username\":\"janek007\",\"password\":\"qwerty\"}")
                .post("/api/v1/login")
                .then().statusCode(200).header("Authentication", startsWith("Bearer "));
    }

    @Test
    void badCredentialsAndMalformedLoginBodiesAreRejected() {
        loginExpecting401("{\"username\":\"janek007\",\"password\":\"wrong\"}");
        loginExpecting401("{\"username\":\"nobody\",\"password\":\"qwerty\"}");
        loginExpecting401("{\"username\":\"janek007\"}");
        loginExpecting401("not json");
    }

    @Test
    void protectedEndpointsRequireAToken() {
        anonymous().get("/api/v1/chats").then().statusCode(401);
        anonymous().get("/api/v1/chats/1").then().statusCode(401);
        anonymous().get("/api/v1/messages/1").then().statusCode(401);
        anonymous().get("/api/v1/users?query=ewa").then().statusCode(401);
        anonymous().post("/api/v1/chats").then().statusCode(401);
        anonymous().put("/api/v1/status").then().statusCode(401);
    }

    @Test
    void formerlyOpenLegacyEndpointsAreProtected() {
        anonymous().get("/test-s3").then().statusCode(401);
        anonymous().get("/images/x.png").then().statusCode(401);
        anonymous().get("/swagger-ui.html").then().statusCode(401);
    }

    @Test
    void invalidTokensAreRejected() {
        String expired = "Bearer " + JWT.create().withClaim("username", "janek007")
                .withExpiresAt(new Date(System.currentTimeMillis() - 1000)).sign(Algorithm.HMAC256(SECRET));
        String wrongKey = "Bearer " + JWT.create().withClaim("username", "janek007")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256("another-secret-another-secret-12345"));
        String unknownUser = "Bearer " + JWT.create().withClaim("username", "deleted-user")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(Algorithm.HMAC256(SECRET));

        for (String token : new String[]{"Bearer garbage", expired, wrongKey, unknownUser}) {
            as(token).get("/api/v1/chats/1").then().statusCode(401);
        }
    }

    @Test
    void errorStatusesAreNotMaskedAs401() {
        // Spring's error dispatch to /error used to be secured, turning every 403/404/400 into a 401
        String token = janek();
        as(token).get("/api/v1/chats/3").then().statusCode(403);
        as(token).get("/api/v1/messages/99999").then().statusCode(404);
        asJson(token, "{\"userIds\":[1]}").post("/api/v1/chats").then().statusCode(400);
    }

    private static String tokenExpiringIn(long millis) {
        return "Bearer " + JWT.create().withClaim("id", 1L).withClaim("username", "janek007")
                .withExpiresAt(new Date(System.currentTimeMillis() + millis)).sign(Algorithm.HMAC256(SECRET));
    }

    @Test
    void aTokenInTheSecondHalfOfItsLifeIsRenewed() {
        as(tokenExpiringIn(60_000)).get("/api/v1/chats/1").then().statusCode(200).header("Authentication", startsWith("Bearer "));
    }

    @Test
    void aFreshTokenIsNotRenewed() {
        as(tokenExpiringIn(3_500_000)).get("/api/v1/chats/1").then().statusCode(200).header("Authentication", nullValue());
    }
}
