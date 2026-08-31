package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.CommunityJoinRequest;
import com.raitukashtam.mycommunity.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityJoinRequestRepository extends JpaRepository<CommunityJoinRequest, Long> {

    List<CommunityJoinRequest> findByCommunity_IdAndStatus(Long communityId, JoinRequestStatus status);

    Optional<CommunityJoinRequest> findByIdAndCommunity_Id(Long id, Long communityId);

    boolean existsByCommunity_IdAndRequesterIdentityIdAndStatus(
            Long communityId, String requesterIdentityId, JoinRequestStatus status);
}
