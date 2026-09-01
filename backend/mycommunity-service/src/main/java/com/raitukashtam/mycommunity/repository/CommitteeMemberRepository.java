package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.CommitteeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, Long> {

    List<CommitteeMember> findByCommunity_IdAndTermEndIsNullOrderByPositionAsc(Long communityId);

    List<CommitteeMember> findByCommunity_IdOrderByTermStartDesc(Long communityId);

    Optional<CommitteeMember> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByMember_IdAndTermEndIsNull(Long memberId);
}
