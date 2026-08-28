package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Slf4j
public class CommunityService {
    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Transactional
    public CommunityResponse createCommunity(CommunityRequest request, String callerIdentityId) {
        log.info("Inside createCommunity for identity: {}", callerIdentityId);

        Community community = new Community();
        community.setName(request.getName());
        community.setTotalUnits(request.getTotalUnits());
        community.setStreet(request.getStreet());
        community.setArea(request.getArea());
        community.setDistrict(request.getDistrict());
        community.setState(request.getState());
        community.setPincode(request.getPincode());
        community.setLandmark(request.getLandmark());
        Community savedCommunity = communityRepository.save(community);

        CommunityMember admin = new CommunityMember();
        admin.setCommunity(savedCommunity);
        admin.setName("Community Admin");
        admin.setUnitNumber("-");
        admin.setMobileNumber(request.getAdminMobile());
        admin.setRole(CommunityRole.ADMIN);
        admin.setStatus(MemberStatus.ACTIVE);
        admin.setIdentityId(callerIdentityId);
        communityMemberRepository.save(admin);

        return toResponse(savedCommunity);
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunity(Long communityId, String callerIdentityId) {
        Community community = requireActiveMember(communityId, callerIdentityId).getCommunity();
        return toResponse(community);
    }

    @Transactional
    public CommunityMemberResponse addMember(Long communityId, CommunityMemberRequest request, String callerIdentityId) {
        requireActiveAdmin(communityId, callerIdentityId);

        if (communityMemberRepository.existsByCommunity_IdAndMobileNumber(communityId, request.getMobileNumber())) {
            throw new ResourceAlreadyExistsException(
                    "A member with mobile number '" + request.getMobileNumber() + "' already exists in this community");
        }

        Community community = communityRepository.getReferenceById(communityId);
        CommunityMember member = new CommunityMember();
        member.setCommunity(community);
        member.setName(request.getName());
        member.setUnitNumber(request.getUnitNumber());
        member.setMobileNumber(request.getMobileNumber());
        member.setRole(CommunityRole.OWNER);
        member.setStatus(MemberStatus.INVITED);
        CommunityMember saved = communityMemberRepository.save(member);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommunityMemberResponse> listMembers(Long communityId, String callerIdentityId) {
        requireActiveMember(communityId, callerIdentityId);
        return communityMemberRepository.findByCommunity_Id(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeMember(Long communityId, Long memberId, String callerIdentityId) {
        requireActiveAdmin(communityId, callerIdentityId);

        CommunityMember member = communityMemberRepository.findByIdAndCommunity_Id(memberId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        if (member.getRole() == CommunityRole.ADMIN
                && communityMemberRepository.countByCommunity_IdAndRoleAndStatus(communityId, CommunityRole.ADMIN, MemberStatus.ACTIVE) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last remaining admin of this community");
        }

        communityMemberRepository.delete(member);
    }

    private CommunityMember requireActiveMember(Long communityId, String callerIdentityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new ResourceNotFoundException("Community not found with id: " + communityId);
        }
        return communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(communityId, callerIdentityId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Not an active member of this community"));
    }

    private CommunityMember requireActiveAdmin(Long communityId, String callerIdentityId) {
        CommunityMember member = requireActiveMember(communityId, callerIdentityId);
        if (member.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Admin role required for this operation");
        }
        return member;
    }

    private CommunityResponse toResponse(Community community) {
        return new CommunityResponse(
                community.getId(),
                community.getName(),
                community.getTotalUnits(),
                community.getStreet(),
                community.getArea(),
                community.getDistrict(),
                community.getState(),
                community.getPincode(),
                community.getLandmark(),
                community.getCreatedAt());
    }

    private CommunityMemberResponse toResponse(CommunityMember member) {
        return new CommunityMemberResponse(
                member.getId(),
                member.getCommunity().getId(),
                member.getName(),
                member.getUnitNumber(),
                member.getMobileNumber(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt());
    }
}
