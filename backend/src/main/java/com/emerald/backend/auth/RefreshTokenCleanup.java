package com.emerald.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes refresh-token rows of sessions that ended more than a day ago, so the
 * table does not grow forever. Rows of recently ended sessions are kept for a
 * day so that replay of an old token is still detected and logged.
 */
@Configuration
@EnableScheduling
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository tokens;

    public RefreshTokenCleanup(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeEndedSessions() {
        int removed = tokens.deleteSessionsEndedBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Removed {} expired refresh-token rows", removed);
        }
    }
}
