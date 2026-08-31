package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Complaint;
import com.raitukashtam.mycommunity.entity.ComplaintPriority;
import com.raitukashtam.mycommunity.entity.ComplaintStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ComplaintRepository;
import com.raitukashtam.mycommunity.request.AssignComplaintRequest;
import com.raitukashtam.mycommunity.request.ComplaintRequest;
import com.raitukashtam.mycommunity.request.ComplaintStatusRequest;
import com.raitukashtam.mycommunity.response.ComplaintResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Any ACTIVE member raises a complaint for themselves; ADMIN triages
 * (assign/status). A complaint is visible to ADMIN, its raiser, or its
 * current assignee -- an extension of the owner-or-admin shape used
 * elsewhere, widened to include the assignee since they need visibility
 * to work the ticket. Status is a strictly linear lifecycle (OPEN ->
 * IN_PROGRESS -> RESOLVED -> CLOSED, one step at a time) -- no skipping
 * or reopening in this phase. Membership authorization is delegated to
 * CommunityService, same pattern as every other phase.
 */
@Service
@Slf4j
public class ComplaintService {
    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public ComplaintResponse createComplaint(Long communityId, ComplaintRequest request, String callerIdentityId) {
        CommunityMember raiser = communityService.requireActiveMember(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Complaint complaint = new Complaint();
        complaint.setCommunity(community);
        complaint.setRaisedBy(raiser);
        complaint.setCategory(request.getCategory().trim());
        complaint.setTitle(request.getTitle().trim());
        complaint.setDescription(request.getDescription().trim());
        complaint.setPriority(request.getPriority() != null ? request.getPriority() : ComplaintPriority.MEDIUM);
        complaint.setStatus(ComplaintStatus.OPEN);
        Complaint saved = complaintRepository.save(complaint);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listComplaints(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return complaintRepository.findByCommunity_IdOrderByCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listMyComplaints(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return complaintRepository.findByRaisedBy_IdOrderByCreatedAtDesc(caller.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(Long communityId, Long complaintId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Complaint complaint = requireComplaint(communityId, complaintId);
        requireVisible(complaint, caller);
        return toResponse(complaint);
    }

    @Transactional
    public ComplaintResponse assignComplaint(Long communityId, Long complaintId, AssignComplaintRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Complaint complaint = requireComplaint(communityId, complaintId);
        CommunityMember assignee = communityMemberRepository.findByIdAndCommunity_Id(request.getAssigneeMemberId(), communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + request.getAssigneeMemberId()));
        complaint.setAssignedTo(assignee);
        return toResponse(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintResponse updateStatus(Long communityId, Long complaintId, ComplaintStatusRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Complaint complaint = requireComplaint(communityId, complaintId);

        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus target = request.getStatus();
        if (target.ordinal() != current.ordinal() + 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot move status from " + current + " to " + target + " -- must advance one step at a time");
        }
        complaint.setStatus(target);
        return toResponse(complaintRepository.save(complaint));
    }

    private void requireVisible(Complaint complaint, CommunityMember caller) {
        boolean isRaiser = complaint.getRaisedBy().getId().equals(caller.getId());
        boolean isAssignee = complaint.getAssignedTo() != null && complaint.getAssignedTo().getId().equals(caller.getId());
        if (!isRaiser && !isAssignee && caller.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Not authorized to view this complaint");
        }
    }

    /** Package-private -- reused by ComplaintCommentService so commenting doesn't duplicate the "complaint exists in this community" lookup and visibility check. */
    Complaint requireComplaint(Long communityId, Long complaintId) {
        return complaintRepository.findByIdAndCommunity_Id(complaintId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found with id: " + complaintId));
    }

    void requireVisibleToCaller(Complaint complaint, CommunityMember caller) {
        requireVisible(complaint, caller);
    }

    ComplaintResponse toResponse(Complaint complaint) {
        return new ComplaintResponse(
                complaint.getId(),
                complaint.getCommunity().getId(),
                complaint.getRaisedBy().getId(),
                complaint.getRaisedBy().getName(),
                complaint.getCategory(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getAssignedTo() != null ? complaint.getAssignedTo().getId() : null,
                complaint.getAssignedTo() != null ? complaint.getAssignedTo().getName() : null,
                complaint.getCreatedAt());
    }
}
