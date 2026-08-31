package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findByCommunity_IdOrderByNameAsc(Long communityId);

    Optional<Staff> findByIdAndCommunity_Id(Long id, Long communityId);
}
