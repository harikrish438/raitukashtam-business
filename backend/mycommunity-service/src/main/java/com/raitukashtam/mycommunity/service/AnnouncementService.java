package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Announcement;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AnnouncementRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AnnouncementRequest;
import com.raitukashtam.mycommunity.response.AnnouncementResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Membership authorization (requireActiveMember/requireActiveAdmin) is
 * delegated to CommunityService, the single source of truth for "who can
 * act in this community" -- same pattern CommunityJoinRequestService uses.
 */
@Service
@Slf4j
public class AnnouncementService {
    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public AnnouncementResponse createAnnouncement(Long communityId, AnnouncementRequest request, String callerIdentityId) {
        CommunityMember poster = communityService.requireActiveAdmin(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Announcement announcement = new Announcement();
        announcement.setCommunity(community);
        announcement.setTitle(request.getTitle().trim());
        announcement.setBody(request.getBody().trim());
        announcement.setPostedBy(poster);
        Announcement saved = announcementRepository.save(announcement);

        List<CommunityMember> recipients = communityMemberRepository.findByCommunity_IdAndStatus(communityId, MemberStatus.ACTIVE).stream()
                .filter(member -> !member.getId().equals(poster.getId()))
                .toList();
        notificationService.notifyMembers(recipients, "New announcement: " + announcement.getTitle(), announcement.getBody());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAnnouncements(Long communityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return announcementRepository.findByCommunity_IdOrderByCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getAnnouncement(Long communityId, Long announcementId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return toResponse(requireAnnouncement(communityId, announcementId));
    }

    @Transactional
    public void deleteAnnouncement(Long communityId, Long announcementId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        announcementRepository.delete(requireAnnouncement(communityId, announcementId));
    }

    private Announcement requireAnnouncement(Long communityId, Long announcementId) {
        return announcementRepository.findByIdAndCommunity_Id(announcementId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found with id: " + announcementId));
    }

    /** Package-private -- reused by DashboardService's Recent Announcements/Activity sections. */
    AnnouncementResponse toResponse(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getCommunity().getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getPostedBy().getId(),
                announcement.getPostedBy().getName(),
                announcement.getCreatedAt());
    }
}
