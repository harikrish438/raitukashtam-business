package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByCommunity_IdOrderByNameAsc(Long communityId);

    Optional<Vendor> findByIdAndCommunity_Id(Long id, Long communityId);
}
