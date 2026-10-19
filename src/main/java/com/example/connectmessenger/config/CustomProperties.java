package com.example.connectmessenger.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "custom")
@Validated
public record CustomProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @Positive long jwtExpirationTime,
        @NotEmpty List<String> allowedOrigins,
        @NotBlank String s3Bucket,
        @NotBlank String awsRegion,
        // status updates are also sent to /topic/messages/{chatId} (old clients) besides /topic/status/{chatId}
        @DefaultValue("true") boolean legacyStatusBroadcast
) {
}
