package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.MembershipStatus;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.ProductStatus;
import com.raitukashtam.auth.entity.Tenant;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 1 of the multi-product identity platform migration: seeds the default
 * ("raitukashtam") Product row and backfills existing identity / tenant rows
 * against it. Idempotent — safe to run on every startup. Runs after
 * IdentityDataSeeder, since membership backfill is keyed on Identity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class ProductDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductMembershipRepository productMembershipRepository;
    private final IdentityRepository identityRepository;
    private final TenantRepository tenantRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    @Value("${raitukashtam.default-product-name:Raitukashtam}")
    private String defaultProductName;

    @Override
    @Transactional
    public void run(String... args) {
        // Phase 2 repointed product_membership from user_id to identity_id.
        // ddl-auto:update never drops columns/constraints on its own, so the
        // old NOT NULL user_id column (unmapped since Phase 2) would otherwise
        // reject every new insert. Safe to run every startup.
        entityManager.createNativeQuery("ALTER TABLE product_membership DROP COLUMN IF EXISTS user_id").executeUpdate();

        Product defaultProduct = productRepository.findByCode(defaultProductCode)
                .orElseGet(() -> {
                    Product product = new Product();
                    product.setCode(defaultProductCode);
                    product.setName(defaultProductName);
                    product.setStatus(ProductStatus.ACTIVE);
                    Product saved = productRepository.save(product);
                    log.info("Seeded default product '{}'", defaultProductCode);
                    return saved;
                });

        // Phase-1-era rows were keyed on user_id, which this entity no longer maps.
        productMembershipRepository.deleteAllWithNullIdentity();

        int membershipsCreated = 0;
        for (Identity identity : identityRepository.findAll()) {
            if (!productMembershipRepository.existsByIdentity_IdAndProduct_Code(identity.getId(), defaultProductCode)) {
                ProductMembership membership = new ProductMembership();
                membership.setIdentity(identity);
                membership.setProduct(defaultProduct);
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setJoinedAt(LocalDateTime.now());
                productMembershipRepository.save(membership);
                membershipsCreated++;
            }
        }
        if (membershipsCreated > 0) {
            log.info("Backfilled {} product_membership row(s) for existing identities", membershipsCreated);
        }

        int tenantsBackfilled = 0;
        for (Tenant tenant : tenantRepository.findAll()) {
            if (tenant.getProduct() == null) {
                tenant.setProduct(defaultProduct);
                tenantRepository.save(tenant);
                tenantsBackfilled++;
            }
        }
        if (tenantsBackfilled > 0) {
            log.info("Backfilled product_id for {} existing tenant(s)", tenantsBackfilled);
        }
    }
}
