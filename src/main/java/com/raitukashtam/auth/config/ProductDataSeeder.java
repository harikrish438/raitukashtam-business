package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.MembershipStatus;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.ProductStatus;
import com.raitukashtam.auth.entity.Tenant;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.TenantRepository;
import com.raitukashtam.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 1 of the multi-product identity platform migration: seeds the default
 * ("raitukashtam") Product row and backfills existing app_user / tenant rows
 * against it. Idempotent — safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductMembershipRepository productMembershipRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    @Value("${raitukashtam.default-product-name:Raitukashtam}")
    private String defaultProductName;

    @Override
    @Transactional
    public void run(String... args) {
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

        int membershipsCreated = 0;
        for (User user : userRepository.findAll()) {
            if (!productMembershipRepository.existsByUser_IdAndProduct_Code(user.getId(), defaultProductCode)) {
                ProductMembership membership = new ProductMembership();
                membership.setUser(user);
                membership.setProduct(defaultProduct);
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setJoinedAt(LocalDateTime.now());
                productMembershipRepository.save(membership);
                membershipsCreated++;
            }
        }
        if (membershipsCreated > 0) {
            log.info("Backfilled {} product_membership row(s) for existing users", membershipsCreated);
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
