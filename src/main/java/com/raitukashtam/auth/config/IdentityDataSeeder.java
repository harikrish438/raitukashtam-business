package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.IdentityCredential;
import com.raitukashtam.auth.entity.IdentityStatus;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.repository.IdentityCredentialRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 2 of the multi-product identity platform migration: for every
 * existing app_user without a linked Identity, creates one (plus a PASSWORD
 * IdentityCredential carrying the existing BCrypt hash) and links it back.
 * Idempotent — safe to run on every startup. Must run before
 * ProductDataSeeder, which now backfills product_membership off Identity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class IdentityDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final IdentityRepository identityRepository;
    private final IdentityCredentialRepository identityCredentialRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        // password/mobile_number predate this phase as NOT NULL; ddl-auto:update
        // never relaxes an existing constraint on its own. Both are now optional
        // (password lives on IdentityCredential; Google sign-in has no phone).
        // Safe to run every startup.
        entityManager.createNativeQuery("ALTER TABLE app_user ALTER COLUMN password DROP NOT NULL").executeUpdate();
        entityManager.createNativeQuery("ALTER TABLE app_user ALTER COLUMN mobile_number DROP NOT NULL").executeUpdate();
        // refresh_token.username was replaced by identity_id (UUID); the old
        // NOT NULL column would otherwise reject every new refresh token row.
        entityManager.createNativeQuery("ALTER TABLE refresh_token DROP COLUMN IF EXISTS username").executeUpdate();

        int backfilled = 0;
        for (User user : userRepository.findAll()) {
            if (user.getIdentity() != null) {
                continue;
            }

            Identity identity = identityRepository.findByPrimaryEmail(user.getEmail())
                    .orElseGet(() -> {
                        Identity newIdentity = new Identity();
                        newIdentity.setPrimaryEmail(user.getEmail());
                        newIdentity.setPrimaryPhone(user.getMobileNumber());
                        newIdentity.setStatus(IdentityStatus.ACTIVE);
                        return identityRepository.save(newIdentity);
                    });

            if (user.getPassword() != null
                    && identityCredentialRepository.findByIdentity_IdAndCredentialType(identity.getId(), CredentialType.PASSWORD).isEmpty()) {
                IdentityCredential credential = new IdentityCredential();
                credential.setIdentity(identity);
                credential.setCredentialType(CredentialType.PASSWORD);
                credential.setPasswordHash(user.getPassword());
                credential.setVerified(user.isVerified());
                identityCredentialRepository.save(credential);
            }

            user.setIdentity(identity);
            userRepository.save(user);
            backfilled++;
        }
        if (backfilled > 0) {
            log.info("Backfilled {} identity/identity_credential row(s) for existing users", backfilled);
        }
    }
}
