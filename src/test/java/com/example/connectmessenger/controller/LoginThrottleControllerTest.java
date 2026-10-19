package com.example.connectmessenger.controller;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "custom.login-max-failures=3")
class LoginThrottleControllerTest extends ControllerComponentTest {

    private static io.restassured.response.Response login(String password) {
        return anonymous().contentType(ContentType.JSON)
                .body("{\"username\":\"ewa-nowak\",\"password\":\"" + password + "\"}")
                .post("/api/v1/login");
    }

    @Test
    void guessingIsStoppedEvenWhenTheRightPasswordIsTriedLater() {
        for (int i = 0; i < 3; i++) assertThat(login("wrong" + i).statusCode()).isEqualTo(401);

        var locked = login("abcd");
        assertThat(locked.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(locked.header("Retry-After"))).isPositive();
    }
}
