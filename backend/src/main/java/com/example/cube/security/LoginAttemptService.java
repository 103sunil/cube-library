package com.example.cube.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Locks a username for a while after repeated failed logins.
 * In-memory: each WebLogic node keeps its own counters, and a restart clears them.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final long lockMillis;
    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${cube.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${cube.security.login.lock-minutes:15}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockMillis = lockMinutes * 60_000L;
    }

    /** Remaining lock time in seconds, or 0 if the username may try to log in. */
    public long lockedForSeconds(String username) {
        Attempts a = attempts.get(key(username));
        if (a == null) {
            return 0;
        }
        synchronized (a) {
            long remaining = a.lockedUntil - System.currentTimeMillis();
            return remaining > 0 ? (remaining + 999) / 1000 : 0;
        }
    }

    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        Attempts a = attempts.computeIfAbsent(key(username), k -> new Attempts());
        synchronized (a) {
            if (a.firstFailureAt == 0 || now - a.firstFailureAt > lockMillis) {
                a.firstFailureAt = now;
                a.failures = 0;
            }
            a.failures++;
            if (a.failures >= maxAttempts) {
                a.lockedUntil = now + lockMillis;
                a.failures = 0;
                a.firstFailureAt = 0;
            }
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        private int failures;
        private long firstFailureAt;
        private long lockedUntil;
    }
}
