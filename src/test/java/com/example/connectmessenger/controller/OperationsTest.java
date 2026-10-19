package com.example.connectmessenger.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

class OperationsTest extends ControllerComponentTest {

    @Test
    void healthIsPublicForProbesButMetricsAreNot() {
        anonymous().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
        anonymous().get("/actuator/health/liveness").then().statusCode(200);
        anonymous().get("/actuator/metrics").then().statusCode(401);
        as(janek()).get("/actuator/metrics").then().statusCode(200);
    }

    @Test
    void healthDoesNotLeakDetails() {
        anonymous().get("/actuator/health").then().body("components", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void everyResponseCarriesARequestId() {
        String generated = anonymous().get("/actuator/health").header("X-Request-Id");
        org.assertj.core.api.Assertions.assertThat(generated).isNotBlank();

        anonymous().header("X-Request-Id", "trace-42.a").get("/actuator/health")
                .then().header("X-Request-Id", equalTo("trace-42.a"));
    }

    @Test
    void aSuspiciousRequestIdIsReplaced() {
        anonymous().header("X-Request-Id", "x".repeat(200)).get("/actuator/health")
                .then().header("X-Request-Id", not(equalTo("x".repeat(200))));
    }
}
