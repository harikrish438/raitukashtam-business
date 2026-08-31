package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Visitor;
import com.raitukashtam.mycommunity.entity.VisitorStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.VisitorRepository;
import com.raitukashtam.mycommunity.request.CreateVisitorRequest;
import com.raitukashtam.mycommunity.response.VisitorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Any ACTIVE member can log/check-in/check-out their own visitors (the
 * natural actor is the host resident, not the community admin -- unlike
 * Announcements/Bills/Expenses). ADMIN additionally sees and can act on
 * every visitor in the community, standing in for a dedicated gate-guard
 * role this system doesn't have yet. Membership authorization is
 * delegated to CommunityService, same pattern as every other phase.
 */
@Service
@Slf4j
public class VisitorService {
    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public VisitorResponse createVisitor(Long communityId, CreateVisitorRequest request, String callerIdentityId) {
        CommunityMember host = communityService.requireActiveMember(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Visitor visitor = new Visitor();
        visitor.setCommunity(community);
        visitor.setHost(host);
        visitor.setGuestName(request.getGuestName().trim());
        visitor.setType(request.getType());
        visitor.setPurpose(request.getPurpose() != null ? request.getPurpose().trim() : null);
        if (request.isCheckedInNow()) {
            visitor.setStatus(VisitorStatus.CHECKED_IN);
            visitor.setEntryTime(LocalDateTime.now());
        } else {
            visitor.setStatus(VisitorStatus.EXPECTED);
        }
        Visitor saved = visitorRepository.save(visitor);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> listVisitors(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return visitorRepository.findByCommunity_IdOrderByCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> listMyVisitors(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return visitorRepository.findByHost_IdOrderByCreatedAtDesc(caller.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VisitorResponse getVisitor(Long communityId, Long visitorId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Visitor visitor = requireVisitor(communityId, visitorId);
        requireHostOrAdmin(visitor, caller);
        return toResponse(visitor);
    }

    @Transactional
    public VisitorResponse checkIn(Long communityId, Long visitorId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Visitor visitor = requireVisitor(communityId, visitorId);
        requireHostOrAdmin(visitor, caller);
        if (visitor.getStatus() != VisitorStatus.EXPECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Visitor is not in EXPECTED status");
        }
        visitor.setStatus(VisitorStatus.CHECKED_IN);
        visitor.setEntryTime(LocalDateTime.now());
        return toResponse(visitorRepository.save(visitor));
    }

    @Transactional
    public VisitorResponse checkOut(Long communityId, Long visitorId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Visitor visitor = requireVisitor(communityId, visitorId);
        requireHostOrAdmin(visitor, caller);
        if (visitor.getStatus() != VisitorStatus.CHECKED_IN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Visitor is not in CHECKED_IN status");
        }
        visitor.setStatus(VisitorStatus.CHECKED_OUT);
        visitor.setExitTime(LocalDateTime.now());
        return toResponse(visitorRepository.save(visitor));
    }

    private void requireHostOrAdmin(Visitor visitor, CommunityMember caller) {
        if (!visitor.getHost().getId().equals(caller.getId()) && caller.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Not authorized to act on this visitor");
        }
    }

    private Visitor requireVisitor(Long communityId, Long visitorId) {
        return visitorRepository.findByIdAndCommunity_Id(visitorId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + visitorId));
    }

    private VisitorResponse toResponse(Visitor visitor) {
        return new VisitorResponse(
                visitor.getId(),
                visitor.getCommunity().getId(),
                visitor.getHost().getId(),
                visitor.getHost().getName(),
                visitor.getGuestName(),
                visitor.getType(),
                visitor.getPurpose(),
                visitor.getStatus(),
                visitor.getEntryTime(),
                visitor.getExitTime(),
                visitor.getCreatedAt());
    }
}
