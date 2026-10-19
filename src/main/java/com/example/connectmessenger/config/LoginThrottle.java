package com.example.connectmessenger.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after `maxFailures` failed logins within `window` a key (an IP address, or a
 * username) is locked out for `lockout`. IP and username are counted separately, so one address cannot try many
 * accounts and many addresses cannot hammer one account. A successful login clears only the username's counter
 * (an attacker who owns an account must not be able to reset the counter of their own IP between guesses).
 * State is in memory, so it is per instance and lost on restart.
 */
public class LoginThrottle {
    private static final int PURGE_THRESHOLD = 10_000;

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration window, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String userKey(String username) {
        return "user:" + username.toLowerCase(Locale.ROOT);
    }

    /** How long the caller still has to wait; zero when a login may be attempted. */
    public Duration retryAfter(String ip, String username) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (String key : new String[]{ipKey(ip), userKey(username)}) {
            Attempts a = attempts.get(key);
            if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(now)) {
                Duration left = Duration.between(now, a.lockedUntil());
                if (left.compareTo(wait) > 0) wait = left;
            }
        }
        return wait;
    }

    public void recordFailure(String ip, String username) {
        if (attempts.size() > PURGE_THRESHOLD) purge();
        count(ipKey(ip));
        count(userKey(username));
    }

    public void recordSuccess(String username) {
        attempts.remove(userKey(username));
    }

    private void count(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, old) -> {
            if (old == null || !old.windowStart().plus(window).isAfter(now) && !isLocked(old, now)) {
                old = new Attempts(0, now, null);
            }
            int failures = old.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockout) : old.lockedUntil();
            return new Attempts(failures, old.windowStart(), lockedUntil);
        });
    }

    private static boolean isLocked(Attempts a, Instant now) {
        return a.lockedUntil() != null && a.lockedUntil().isAfter(now);
    }

    /** Drops entries that can no longer matter, so a flood of random usernames cannot fill the memory. */
    private void purge() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> !isLocked(e.getValue(), now) && !e.getValue().windowStart().plus(window).isAfter(now));
    }
}
