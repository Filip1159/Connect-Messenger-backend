package com.example.connectmessenger.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(10), Duration.ofMinutes(15), clock);

    private void fail(String ip, String user, int times) {
        for (int i = 0; i < times; i++) throttle.recordFailure(ip, user);
    }

    @Test
    void locksAUsernameAfterTooManyFailuresFromAnyAddress() {
        fail("1.1.1.1", "ewa", 1);
        fail("2.2.2.2", "ewa", 1);
        assertThat(throttle.retryAfter("3.3.3.3", "ewa")).isZero();
        fail("4.4.4.4", "ewa", 1);
        assertThat(throttle.retryAfter("5.5.5.5", "ewa")).isEqualTo(Duration.ofMinutes(15));
        assertThat(throttle.retryAfter("5.5.5.5", "someone-else")).isZero();
    }

    @Test
    void locksAnAddressThatTriesManyAccounts() {
        fail("1.1.1.1", "a", 1);
        fail("1.1.1.1", "b", 1);
        fail("1.1.1.1", "c", 1);
        assertThat(throttle.retryAfter("1.1.1.1", "d")).isPositive();
        assertThat(throttle.retryAfter("9.9.9.9", "d")).isZero();
    }

    @Test
    void usernamesAreCaseInsensitive() {
        fail("1.1.1.1", "Ewa", 3);
        assertThat(throttle.retryAfter("2.2.2.2", "EWA")).isPositive();
    }

    @Test
    void theLockoutEndsAndCountingStartsOver() {
        fail("1.1.1.1", "ewa", 3);
        clock.advance(Duration.ofMinutes(14));
        assertThat(throttle.retryAfter("2.2.2.2", "ewa")).isEqualTo(Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        assertThat(throttle.retryAfter("2.2.2.2", "ewa")).isZero();
        fail("2.2.2.2", "ewa", 2);
        assertThat(throttle.retryAfter("2.2.2.2", "ewa")).isZero();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        fail("1.1.1.1", "ewa", 2);
        clock.advance(Duration.ofMinutes(11));
        fail("1.1.1.1", "ewa", 2);
        assertThat(throttle.retryAfter("1.1.1.1", "ewa")).isZero();
    }

    @Test
    void aSuccessfulLoginClearsTheUsernameButNotTheAddress() {
        fail("1.1.1.1", "ewa", 2);
        throttle.recordSuccess("ewa");
        fail("2.2.2.2", "ewa", 2);
        assertThat(throttle.retryAfter("2.2.2.2", "ewa")).isZero();
        fail("1.1.1.1", "x", 1);  // the address of the first two failures is now at three
        assertThat(throttle.retryAfter("1.1.1.1", "y")).isPositive();
    }
}
