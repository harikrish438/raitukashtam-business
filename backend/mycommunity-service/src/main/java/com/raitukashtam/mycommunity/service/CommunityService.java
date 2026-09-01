package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.client.AuthServiceClient;
import com.raitukashtam.mycommunity.client.AuthUserProfile;
import com.raitukashtam.mycommunity.entity.BillingMode;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.DuplicateCommunityException;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.BillingSettingsRequest;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.request.MemberProfileUpdateRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.response.MyCommunityResponse;
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

    @Autowired
    private AuthServiceClient authServiceClient;

    @Transactional
    public CommunityResponse createCommunity(CommunityRequest request, String callerIdentityId, String callerToken) {
        log.info("Inside createCommunity for identity: {}", callerIdentityId);

        String name = request.getName().trim();
        String pincode = request.getPincode().trim();
        communityRepository.findByNameIgnoreCaseAndPincode(name, pincode).ifPresent(existing -> {
            throw new DuplicateCommunityException(existing.getId(), existing.getName());
        });

        AuthUserProfile callerProfile = requireCallerProfile(callerToken);

        Community community = new Community();
        community.setName(name);
        community.setTotalUnits(request.getTotalUnits());
        community.setStreet(request.getStreet());
        community.setArea(request.getArea());
        community.setDistrict(request.getDistrict());
        community.setState(request.getState());
        community.setPincode(pincode);
        community.setLandmark(request.getLandmark());
        Community savedCommunity = communityRepository.save(community);

        CommunityMember admin = new CommunityMember();
        admin.setCommunity(savedCommunity);
        admin.setName(displayName(callerProfile));
        admin.setUnitNumber("-");
        admin.setMobileNumber(callerProfile.getMobileNumber());
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

    @Transactional(readOnly = true)
    public List<MyCommunityResponse> listMyCommunities(String callerIdentityId) {
        return communityMemberRepository.findByIdentityId(callerIdentityId).stream()
                .map(member -> new MyCommunityResponse(
                        member.getCommunity().getId(), member.getCommunity().getName(),
                        member.getRole(), member.getStatus()))
                .toList();
    }

    /**
     * Links any INVITED CommunityMember rows matching the caller's real
     * mobile number (resolved from auth-service, never client-supplied) to
     * this identity -- the missing piece that let an admin-invited member
     * finally act in the community once they log in for real. Idempotent:
     * a caller with nothing to activate just gets their current list back.
     */
    @Transactional
    public List<MyCommunityResponse> activateInvitations(String callerIdentityId, String callerToken) {
        AuthUserProfile callerProfile = requireCallerProfile(callerToken);

        List<CommunityMember> invited = communityMemberRepository
                .findByMobileNumberAndStatusAndIdentityIdIsNull(callerProfile.getMobileNumber(), MemberStatus.INVITED);
        for (CommunityMember member : invited) {
            member.setIdentityId(callerIdentityId);
            member.setStatus(MemberStatus.ACTIVE);
        }
        communityMemberRepository.saveAll(invited);

        return listMyCommunities(callerIdentityId);
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
        member.setRole(CommunityRole.RESIDENT);
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

    @Transactional
    public CommunityMemberResponse updateMyProfile(Long communityId, MemberProfileUpdateRequest request, String callerIdentityId) {
        CommunityMember member = requireActiveMember(communityId, callerIdentityId);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank");
            }
            member.setName(request.getName().trim());
        }
        if (request.getEmail() != null) {
            member.setEmail(request.getEmail().trim());
        }
        if (request.getUnitNumber() != null) {
            if (request.getUnitNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit number cannot be blank");
            }
            member.setUnitNumber(request.getUnitNumber().trim());
        }

        CommunityMember saved = communityMemberRepository.save(member);
        return toResponse(saved);
    }

    @Transactional
    public CommunityResponse updateBillingSettings(Long communityId, BillingSettingsRequest request, String callerIdentityId) {
        requireActiveAdmin(communityId, callerIdentityId);

        if (request.getBillingMode() == BillingMode.PER_AREA && request.getRatePerSqft() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate per sqft is required when billing mode is PER_AREA");
        }

        Community community = communityRepository.getReferenceById(communityId);
        community.setBillingMode(request.getBillingMode());
        community.setRatePerSqft(request.getBillingMode() == BillingMode.PER_AREA ? request.getRatePerSqft() : null);

        return toResponse(communityRepository.save(community));
    }

    CommunityMember requireActiveMember(Long communityId, String callerIdentityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new ResourceNotFoundException("Community not found with id: " + communityId);
        }
        return communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(communityId, callerIdentityId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Not an active member of this community"));
    }

    CommunityMember requireActiveAdmin(Long communityId, String callerIdentityId) {
        CommunityMember member = requireActiveMember(communityId, callerIdentityId);
        if (member.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Admin role required for this operation");
        }
        return member;
    }

    private AuthUserProfile requireCallerProfile(String callerToken) {
        AuthUserProfile profile = authServiceClient.getCurrentUserProfile(callerToken);
        if (profile == null || profile.getMobileNumber() == null || profile.getMobileNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not resolve caller's mobile number from auth-service");
        }
        return profile;
    }

    private String displayName(AuthUserProfile profile) {
        String first = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String last = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Community Admin" : full;
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
                community.getBillingMode(),
                community.getRatePerSqft(),
                community.getCreatedAt());
    }

    /** Package-private -- reused by UnitService so assigning a structured Unit to a member doesn't duplicate this mapping. */
    CommunityMemberResponse toResponse(CommunityMember member) {
        return new CommunityMemberResponse(
                member.getId(),
                member.getCommunity().getId(),
                member.getName(),
                member.getUnitNumber(),
                member.getUnit() == null ? null : member.getUnit().getId(),
                member.getMobileNumber(),
                member.getEmail(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt());
    }
}
