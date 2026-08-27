package com.raitukashtam.auth.config;

import com.raitukashtam.auth.repository.IdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the first PLATFORM_ADMIN in an environment with no promote/
 * demote API and no routine direct DB access (test/prod). Idempotent, same
 * pattern as ProductDataSeeder. No-op when unset (dev default). Operational
 * flow: deploy -> register the intended admin's email via the normal public
 * /users/register endpoint -> restart this service once -> this seeder
 * promotes it on that boot. Deliberately reuses the existing registration
 * flow rather than inventing a second user-creation path.
 *
 * Lookup is an exact-match on email (IdentityRepository.findByPrimaryEmail
 * -- no normalization anywhere in this codebase), so
 * raitukashtam.platform-admin.email must match the registered email's
 * exact case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class PlatformAdminSeeder implements CommandLineRunner {

    private final IdentityRepository identityRepository;

    @Value("${raitukashtam.platform-admin.email:}")
    private String platformAdminEmail;

    @Override
    @Transactional
    public void run(String... args) {
        if (platformAdminEmail == null || platformAdminEmail.isBlank()) {
            return;
        }

        identityRepository.findByPrimaryEmail(platformAdminEmail).ifPresentOrElse(identity -> {
            if (!identity.isPlatformAdmin()) {
                identity.setPlatformAdmin(true);
                identityRepository.save(identity);
                log.info("Promoted '{}' to PLATFORM_ADMIN", platformAdminEmail);
            }
        }, () -> log.warn("PLATFORM_ADMIN bootstrap email '{}' has no registered identity yet -- "
                + "register it via /users/register, then restart this service to promote it.", platformAdminEmail));
    }
}
