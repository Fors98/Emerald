package com.emerald.backend.auth;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter for FAILED attempts, keyed by client address.
 *
 * Only failures are counted and a later success does not reset the counter, so
 * an attacker cannot buy fresh guesses by logging in to an account they own.
 *
 * State is in memory, which is correct for a single application instance. If
 * the backend is ever scaled to several instances, enforce the same limit at
 * the reverse proxy / API gateway or move this state to a shared store.
 * (Per-account lockout is stored in the database and is already cluster-safe.)
 */
final class AttemptLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxFailures;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> failures = new ConcurrentHashMap<>();

    AttemptLimiter(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.windowMillis = window.toMillis();
    }

    /** Throws {@link AuthException.TooManyAttempts} when the key has used up its failures. */
    void assertAllowed(String key) {
        long now = System.currentTimeMillis();
        long[] retryAfterMillis = {0};

        failures.computeIfPresent(key, (k, queue) -> {
            prune(queue, now);
            if (queue.size() >= maxFailures) {
                retryAfterMillis[0] = queue.peekFirst() + windowMillis - now;
            }
            return queue.isEmpty() ? null : queue;
        });

        if (retryAfterMillis[0] > 0) {
            throw new AuthException.TooManyAttempts(Math.max(1, (retryAfterMillis[0] + 999) / 1000));
        }
    }

    void recordFailure(String key) {
        long now = System.currentTimeMillis();
        if (failures.size() > PURGE_THRESHOLD) {
            purge(now);
        }
        failures.compute(key, (k, queue) -> {
            ArrayDeque<Long> q = queue == null ? new ArrayDeque<>() : queue;
            prune(q, now);
            if (q.size() < maxFailures) {
                q.addLast(now);
            }
            return q;
        });
    }

    private void prune(ArrayDeque<Long> queue, long now) {
        while (!queue.isEmpty() && now - queue.peekFirst() >= windowMillis) {
            queue.removeFirst();
        }
    }

    private void purge(long now) {
        // Per-key atomic update: never touch a queue outside compute*().
        for (String key : failures.keySet()) {
            failures.computeIfPresent(key, (k, queue) -> {
                prune(queue, now);
                return queue.isEmpty() ? null : queue;
            });
        }
    }
}
