package com.example.connectmessenger.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

class UserControllerTest extends ControllerComponentTest {

    @Test
    void searchIsCaseInsensitiveAndNeverExposesPasswords() {
        as(janek()).get("/user/EWA")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].username", equalTo("ewa-nowak"))
                .body("[0].password", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void searchExcludesTheCaller() {
        as(janek()).get("/user/janek").then().statusCode(200).body("$", empty());
    }

    @Test
    void searchNeedsAtLeastTwoCharacters() {
        as(janek()).get("/user/e").then().statusCode(200).body("$", empty());
    }
}
