package com.example.connectmessenger.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;

class PresenceControllerTest extends ControllerComponentTest {

    @Test
    void presenceListsContactsOfTheUser() {
        as(janek()).get("/presence")
                .then().statusCode(200)
                .body("userId", containsInAnyOrder(2, 3, 4))
                .body("online", everyItem(equalTo(false)));
    }

    @Test
    void presenceRequiresAuthentication() {
        anonymous().get("/presence").then().statusCode(401);
    }
}
