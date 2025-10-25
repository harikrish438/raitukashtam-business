package com.raitukashtam.auth.scheduler;

import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.TokenBlackListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Component
public class TokenCleanupTask {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TokenBlackListRepository blacklistRepository;

    /*public TokenCleanupTask(RefreshTokenRepository refreshTokenRepository,
                            TokenBlackListRepository blacklistRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistRepository = blacklistRepository;
    }*/

    // Run every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanUpExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredSince(LocalDateTime.now());
        blacklistRepository.deleteAllExpiredSince(new Date().toInstant());
        System.out.println("✅ Token cleanup completed at " + LocalDateTime.now());
    }
}


