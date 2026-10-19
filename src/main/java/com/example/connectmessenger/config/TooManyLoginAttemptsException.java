package com.example.connectmessenger.config;

import org.springframework.security.core.AuthenticationException;

import java.time.Duration;

public class TooManyLoginAttemptsException extends AuthenticationException {
    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed login attempts");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
