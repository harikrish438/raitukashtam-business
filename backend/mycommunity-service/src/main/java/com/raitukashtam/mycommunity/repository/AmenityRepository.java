package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    List<Amenity> findByCommunity_IdOrderByNameAsc(Long communityId);

    Optional<Amenity> findByIdAndCommunity_Id(Long id, Long communityId);
}
