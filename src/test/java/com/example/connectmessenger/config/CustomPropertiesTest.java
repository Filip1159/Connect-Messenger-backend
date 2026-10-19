package com.example.connectmessenger.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomPropertiesTest {
    private static int violationsFor(String secret) {
        var properties = new CustomProperties(secret, 3_600_000, List.of("http://localhost"), "bucket", "eu-central-1",
                true, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties).size();
        }
    }

    @Test
    void aStrongSecretIsAccepted() {
        assertThat(violationsFor("0123456789abcdef0123456789abcdef")).isZero();
    }

    @Test
    void shortAndPublishedSecretsAreRefused() {
        assertThat(violationsFor("short")).isPositive();
        assertThat(violationsFor("vegetables")).isPositive();
        assertThat(violationsFor("a-long-enough-prefix-but-vegetables-inside")).isPositive();
    }
}
