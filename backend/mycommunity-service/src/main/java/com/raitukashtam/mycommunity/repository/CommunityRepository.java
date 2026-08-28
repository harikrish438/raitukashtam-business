package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    /**
     * Find a community by its exact name
     * @param name the name of the community to find
     * @return an Optional containing the community if found, empty otherwise
     */
    Optional<Community> findByName(String name);

    /**
     * Check if a community with the given name exists (case-insensitive)
     * @param name the name to check
     * @return true if a community with the name exists (case-insensitive), false otherwise
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Check if a community with the given name exists
     * @param name the name to check
     * @return true if a community with the name exists, false otherwise
     */
    boolean existsByName(String name);
}
