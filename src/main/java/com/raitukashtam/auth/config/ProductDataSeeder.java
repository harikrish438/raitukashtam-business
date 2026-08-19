package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductStatus;
import com.raitukashtam.auth.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the default ("raitukashtam") Product row if it doesn't already
 * exist. Idempotent reference-data seed — safe to run on every startup,
 * including a fresh test/prod database's first boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    @Value("${raitukashtam.default-product-name:Raitukashtam}")
    private String defaultProductName;

    @Override
    @Transactional
    public void run(String... args) {
        productRepository.findByCode(defaultProductCode)
                .orElseGet(() -> {
                    Product product = new Product();
                    product.setCode(defaultProductCode);
                    product.setName(defaultProductName);
                    product.setStatus(ProductStatus.ACTIVE);
                    Product saved = productRepository.save(product);
                    log.info("Seeded default product '{}'", defaultProductCode);
                    return saved;
                });
    }
}
