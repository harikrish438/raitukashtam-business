package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByCommunity_IdOrderByCreatedAtDesc(Long communityId);

    List<Complaint> findByRaisedBy_IdOrderByCreatedAtDesc(Long raisedByMemberId);

    Optional<Complaint> findByIdAndCommunity_Id(Long id, Long communityId);
}
