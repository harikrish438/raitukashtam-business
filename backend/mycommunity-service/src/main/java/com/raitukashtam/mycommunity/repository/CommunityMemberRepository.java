package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {

    Optional<CommunityMember> findByCommunity_IdAndIdentityIdAndStatus(
            Long communityId, String identityId, MemberStatus status);

    boolean existsByCommunity_IdAndMobileNumber(Long communityId, String mobileNumber);

    List<CommunityMember> findByCommunity_Id(Long communityId);

    Optional<CommunityMember> findByIdAndCommunity_Id(Long id, Long communityId);

    long countByCommunity_IdAndRoleAndStatus(Long communityId, CommunityRole role, MemberStatus status);

    List<CommunityMember> findByIdentityId(String identityId);

    List<CommunityMember> findByMobileNumberAndStatusAndIdentityIdIsNull(String mobileNumber, MemberStatus status);
}
