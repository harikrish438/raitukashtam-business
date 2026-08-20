package com.raitukashtam.auth.scheduler;

import com.raitukashtam.auth.repository.RefreshTokenLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phase 4b: the old RefreshToken/TokenBlackList tables are gone, replaced
 * by Spring's own oauth2_authorization table (which it never cleans up
 * itself) and refresh_token_ledger (this service's own reuse-detection
 * history). Both need the same periodic pruning the old tables got.
 */
@Component
public class TokenCleanupTask {

    @Autowired
    private RefreshTokenLedgerRepository refreshTokenLedgerRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Run every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanUpExpiredTokens() {
        // Ledger entries only matter until their authorization's refresh
        // token would have naturally expired anyway; a generous 30-day
        // retention is far past any realistic refresh-token TTL.
        refreshTokenLedgerRepository.deleteAllCreatedBefore(Instant.now().minusSeconds(30L * 24 * 3600));
        jdbcTemplate.update(
                "DELETE FROM oauth2_authorization WHERE "
                        + "(refresh_token_expires_at IS NOT NULL AND refresh_token_expires_at < now()) "
                        + "OR (refresh_token_expires_at IS NULL AND access_token_expires_at < now())");
    }
}
