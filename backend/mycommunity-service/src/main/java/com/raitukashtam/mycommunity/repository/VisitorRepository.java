package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    List<Visitor> findByCommunity_IdOrderByCreatedAtDesc(Long communityId);

    List<Visitor> findByHost_IdOrderByCreatedAtDesc(Long hostMemberId);

    Optional<Visitor> findByIdAndCommunity_Id(Long id, Long communityId);
}
