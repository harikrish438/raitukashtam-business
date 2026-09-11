package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findByNameIgnoreCaseAndPincode(String name, String pincode);

    List<Community> findByNameContainingIgnoreCase(String name);
}
