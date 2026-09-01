package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.CommitteeMember;
import com.raitukashtam.mycommunity.entity.CommitteePosition;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommitteeMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.CommitteeMemberRequest;
import com.raitukashtam.mycommunity.response.CommitteeMemberResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Directory + term-tracking only -- a CommitteeMember seat is layered on
 * an existing CommunityMember (ADMIN or RESIDENT) and grants no extra
 * authorization in this phase, keeping the two-role model intact rather
 * than a larger authorization change touching every service. ADMIN
 * manages (create/end-term); any ACTIVE member browses the directory,
 * matching Staff/Vendor's ADMIN-only precedent for management but
 * opening reads to everyone since a committee roster is member-facing
 * (unlike Staff/Vendor, which no app screen drives). Membership
 * authorization is delegated to CommunityService, same pattern as every
 * other phase.
 */
@Service
@Slf4j
public class CommitteeService {
    @Autowired
    private CommitteeMemberRepository committeeMemberRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public CommitteeMemberResponse createCommitteeMember(Long communityId, CommitteeMemberRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        if (request.getPosition() == CommitteePosition.OTHER
                && (request.getCustomPosition() == null || request.getCustomPosition().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom position is required when position is OTHER");
        }

        CommunityMember member = communityMemberRepository.findByIdAndCommunity_Id(request.getMemberId(), communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + request.getMemberId()));

        if (committeeMemberRepository.existsByMember_IdAndTermEndIsNull(member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This member already holds a current committee position");
        }

        Community community = communityRepository.getReferenceById(communityId);
        CommitteeMember committeeMember = new CommitteeMember();
        committeeMember.setCommunity(community);
        committeeMember.setMember(member);
        committeeMember.setPosition(request.getPosition());
        committeeMember.setCustomPosition(request.getPosition() == CommitteePosition.OTHER ? request.getCustomPosition().trim() : null);
        committeeMember.setTermStart(request.getTermStart() != null ? request.getTermStart() : LocalDate.now());

        return toResponse(committeeMemberRepository.save(committeeMember));
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponse> listCurrentCommittee(Long communityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return committeeMemberRepository.findByCommunity_IdAndTermEndIsNullOrderByPositionAsc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponse> listCommitteeHistory(Long communityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return committeeMemberRepository.findByCommunity_IdOrderByTermStartDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommitteeMemberResponse getCommitteeMember(Long communityId, Long committeeMemberId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return toResponse(requireCommitteeMember(communityId, committeeMemberId));
    }

    @Transactional
    public CommitteeMemberResponse endTerm(Long communityId, Long committeeMemberId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        CommitteeMember committeeMember = requireCommitteeMember(communityId, committeeMemberId);
        if (committeeMember.getTermEnd() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This committee term has already ended");
        }
        committeeMember.setTermEnd(LocalDate.now());
        return toResponse(committeeMemberRepository.save(committeeMember));
    }

    private CommitteeMember requireCommitteeMember(Long communityId, Long committeeMemberId) {
        return committeeMemberRepository.findByIdAndCommunity_Id(committeeMemberId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Committee member not found with id: " + committeeMemberId));
    }

    private CommitteeMemberResponse toResponse(CommitteeMember committeeMember) {
        return new CommitteeMemberResponse(
                committeeMember.getId(),
                committeeMember.getCommunity().getId(),
                committeeMember.getMember().getId(),
                committeeMember.getMember().getName(),
                committeeMember.getPosition(),
                committeeMember.getCustomPosition(),
                committeeMember.getTermStart(),
                committeeMember.getTermEnd(),
                committeeMember.getTermEnd() == null);
    }
}
