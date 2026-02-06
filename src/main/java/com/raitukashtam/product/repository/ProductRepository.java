package com.raitukashtam.product.repository;

import com.raitukashtam.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    /**
     * Find a product by its exact name
     * @param name the name of the product to find
     * @return an Optional containing the product if found, empty otherwise
     */
    Optional<Product> findByName(String name);
    
    /**
     * Check if a product with the given name exists (case-insensitive)
     * @param name the name to check
     * @return true if a product with the name exists (case-insensitive), false otherwise
     */
    boolean existsByNameIgnoreCase(String name);
    
    /**
     * Check if a product with the given name exists
     * @param name the name to check
     * @return true if a product with the name exists, false otherwise
     */
    boolean existsByName(String name);
}
