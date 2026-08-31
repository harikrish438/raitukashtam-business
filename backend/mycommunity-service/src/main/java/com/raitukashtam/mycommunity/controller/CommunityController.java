package com.raitukashtam.mycommunity.controller;

import com.raitukashtam.mycommunity.request.AnnouncementRequest;
import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.request.JoinRequestRequest;
import com.raitukashtam.mycommunity.request.MemberProfileUpdateRequest;
import com.raitukashtam.mycommunity.response.AnnouncementResponse;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.response.JoinRequestResponse;
import com.raitukashtam.mycommunity.response.MyCommunityResponse;
import com.raitukashtam.mycommunity.service.AnnouncementService;
import com.raitukashtam.mycommunity.service.CommunityJoinRequestService;
import com.raitukashtam.mycommunity.service.CommunityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/communities")
@Slf4j
public class CommunityController {
    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityJoinRequestService joinRequestService;

    @Autowired
    private AnnouncementService announcementService;

    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(@RequestBody @Validated CommunityRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createCommunity method of CommunityController");
        return new ResponseEntity<>(
                communityService.createCommunity(request, jwt.getSubject(), jwt.getTokenValue()), HttpStatus.CREATED);
    }

    @GetMapping("/mine")
    public List<MyCommunityResponse> listMyCommunities(@AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMyCommunities method of CommunityController");
        return communityService.listMyCommunities(jwt.getSubject());
    }

    @PostMapping("/members/activate-invitations")
    public List<MyCommunityResponse> activateInvitations(@AuthenticationPrincipal Jwt jwt) {
        log.info("Inside activateInvitations method of CommunityController");
        return communityService.activateInvitations(jwt.getSubject(), jwt.getTokenValue());
    }

    @GetMapping("/{communityId}")
    public CommunityResponse getCommunity(@PathVariable("communityId") Long communityId,
                                           @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getCommunity method of CommunityController");
        return communityService.getCommunity(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/members")
    public ResponseEntity<CommunityMemberResponse> addMember(@PathVariable("communityId") Long communityId,
                                                              @RequestBody @Validated CommunityMemberRequest request,
                                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside addMember method of CommunityController");
        return new ResponseEntity<>(communityService.addMember(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/members")
    public List<CommunityMemberResponse> listMembers(@PathVariable("communityId") Long communityId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listMembers method of CommunityController");
        return communityService.listMembers(communityId, jwt.getSubject());
    }

    @PatchMapping("/{communityId}/members/me")
    public CommunityMemberResponse updateMyProfile(@PathVariable("communityId") Long communityId,
                                                     @RequestBody @Validated MemberProfileUpdateRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside updateMyProfile method of CommunityController");
        return communityService.updateMyProfile(communityId, request, jwt.getSubject());
    }

    @DeleteMapping("/{communityId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable("communityId") Long communityId,
                                              @PathVariable("memberId") Long memberId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside removeMember method of CommunityController");
        communityService.removeMember(communityId, memberId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/join-requests")
    public ResponseEntity<JoinRequestResponse> createJoinRequest(@PathVariable("communityId") Long communityId,
                                                                   @RequestBody @Validated JoinRequestRequest request,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createJoinRequest method of CommunityController");
        return new ResponseEntity<>(
                joinRequestService.createJoinRequest(communityId, request, jwt.getSubject(), jwt.getTokenValue()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/join-requests")
    public List<JoinRequestResponse> listJoinRequests(@PathVariable("communityId") Long communityId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listJoinRequests method of CommunityController");
        return joinRequestService.listPendingJoinRequests(communityId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/join-requests/{requestId}/approve")
    public CommunityMemberResponse approveJoinRequest(@PathVariable("communityId") Long communityId,
                                                        @PathVariable("requestId") Long requestId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside approveJoinRequest method of CommunityController");
        return joinRequestService.approveJoinRequest(communityId, requestId, jwt.getSubject());
    }

    @PostMapping("/{communityId}/join-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectJoinRequest(@PathVariable("communityId") Long communityId,
                                                    @PathVariable("requestId") Long requestId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside rejectJoinRequest method of CommunityController");
        joinRequestService.rejectJoinRequest(communityId, requestId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{communityId}/announcements")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(@PathVariable("communityId") Long communityId,
                                                                      @RequestBody @Validated AnnouncementRequest request,
                                                                      @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createAnnouncement method of CommunityController");
        return new ResponseEntity<>(
                announcementService.createAnnouncement(communityId, request, jwt.getSubject()), HttpStatus.CREATED);
    }

    @GetMapping("/{communityId}/announcements")
    public List<AnnouncementResponse> listAnnouncements(@PathVariable("communityId") Long communityId,
                                                          @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside listAnnouncements method of CommunityController");
        return announcementService.listAnnouncements(communityId, jwt.getSubject());
    }

    @GetMapping("/{communityId}/announcements/{announcementId}")
    public AnnouncementResponse getAnnouncement(@PathVariable("communityId") Long communityId,
                                                 @PathVariable("announcementId") Long announcementId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside getAnnouncement method of CommunityController");
        return announcementService.getAnnouncement(communityId, announcementId, jwt.getSubject());
    }

    @DeleteMapping("/{communityId}/announcements/{announcementId}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable("communityId") Long communityId,
                                                    @PathVariable("announcementId") Long announcementId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside deleteAnnouncement method of CommunityController");
        announcementService.deleteAnnouncement(communityId, announcementId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
