package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.client.AuthServiceClient;
import com.raitukashtam.mycommunity.client.AuthUserProfile;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityJoinRequest;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.JoinRequestStatus;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityJoinRequestRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.JoinRequestRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.JoinRequestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The "I wasn't invited, let me ask to join" path -- surfaced to a caller
 * when CommunityService.createCommunity 409s with a DuplicateCommunityException
 * pointing at an existing community. Membership authorization
 * (requireActiveMember/requireActiveAdmin) is delegated to CommunityService
 * rather than duplicated here, since it's the single source of truth for
 * "who can act in this community."
 */
@Service
@Slf4j
public class CommunityJoinRequestService {
    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityJoinRequestRepository joinRequestRepository;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private AuthServiceClient authServiceClient;

    @Transactional
    public JoinRequestResponse createJoinRequest(Long communityId, JoinRequestRequest request, String callerIdentityId, String callerToken) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));

        if (communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(communityId, callerIdentityId, MemberStatus.ACTIVE).isPresent()) {
            throw new ResourceAlreadyExistsException("Already an active member of this community");
        }
        if (joinRequestRepository.existsByCommunity_IdAndRequesterIdentityIdAndStatus(communityId, callerIdentityId, JoinRequestStatus.PENDING)) {
            throw new ResourceAlreadyExistsException("A pending join request already exists for this community");
        }

        AuthUserProfile callerProfile = authServiceClient.getCurrentUserProfile(callerToken);
        if (callerProfile == null || callerProfile.getMobileNumber() == null || callerProfile.getMobileNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not resolve caller's mobile number from auth-service");
        }

        CommunityJoinRequest joinRequest = new CommunityJoinRequest();
        joinRequest.setCommunity(community);
        joinRequest.setRequesterIdentityId(callerIdentityId);
        joinRequest.setRequesterMobileNumber(callerProfile.getMobileNumber());
        joinRequest.setRequesterName(request.getName().trim());
        joinRequest.setStatus(JoinRequestStatus.PENDING);
        CommunityJoinRequest saved = joinRequestRepository.save(joinRequest);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listPendingJoinRequests(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return joinRequestRepository.findByCommunity_IdAndStatus(communityId, JoinRequestStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommunityMemberResponse approveJoinRequest(Long communityId, Long requestId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        CommunityJoinRequest joinRequest = requirePendingRequest(communityId, requestId);

        if (communityMemberRepository.existsByCommunity_IdAndMobileNumber(communityId, joinRequest.getRequesterMobileNumber())) {
            throw new ResourceAlreadyExistsException(
                    "A member with mobile number '" + joinRequest.getRequesterMobileNumber() + "' already exists in this community");
        }

        CommunityMember member = new CommunityMember();
        member.setCommunity(joinRequest.getCommunity());
        member.setName(joinRequest.getRequesterName());
        member.setUnitNumber("-");
        member.setMobileNumber(joinRequest.getRequesterMobileNumber());
        member.setRole(CommunityRole.RESIDENT);
        member.setStatus(MemberStatus.ACTIVE);
        member.setIdentityId(joinRequest.getRequesterIdentityId());
        CommunityMember savedMember = communityMemberRepository.save(member);

        joinRequest.setStatus(JoinRequestStatus.APPROVED);
        joinRequestRepository.save(joinRequest);

        return communityService.toResponse(savedMember);
    }

    @Transactional
    public void rejectJoinRequest(Long communityId, Long requestId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        CommunityJoinRequest joinRequest = requirePendingRequest(communityId, requestId);
        joinRequest.setStatus(JoinRequestStatus.REJECTED);
        joinRequestRepository.save(joinRequest);
    }

    private CommunityJoinRequest requirePendingRequest(Long communityId, Long requestId) {
        CommunityJoinRequest joinRequest = joinRequestRepository.findByIdAndCommunity_Id(requestId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found with id: " + requestId));
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request is not pending");
        }
        return joinRequest;
    }

    private JoinRequestResponse toResponse(CommunityJoinRequest joinRequest) {
        return new JoinRequestResponse(
                joinRequest.getId(),
                joinRequest.getCommunity().getId(),
                joinRequest.getRequesterName(),
                joinRequest.getRequesterMobileNumber(),
                joinRequest.getStatus(),
                joinRequest.getCreatedAt());
    }
}
