package com.example.connectmessenger.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "custom")
@Validated
public record CustomProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @Positive long jwtExpirationTime,
        @NotEmpty List<String> allowedOrigins,
        @NotBlank String s3Bucket,
        @NotBlank String awsRegion,
        // a username or an IP address is locked out for `loginLockout` after this many failed logins within `loginFailureWindow`
        @DefaultValue("5") @Positive int loginMaxFailures,
        @DefaultValue("15m") Duration loginFailureWindow,
        @DefaultValue("15m") Duration loginLockout
) {
    /** Secrets that were committed to the repository: anyone can read them, so they must never sign tokens. */
    private static final Set<String> LEAKED_SECRETS = Set.of("vegetables");

    @AssertTrue(message = "This JWT secret was published in the repository; generate a new one (openssl rand -hex 32)")
    boolean isSecretNotLeaked() {
        return secret == null || LEAKED_SECRETS.stream().noneMatch(secret::contains);
    }
}
