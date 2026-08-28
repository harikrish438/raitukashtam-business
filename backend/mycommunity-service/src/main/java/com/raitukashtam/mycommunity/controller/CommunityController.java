package com.raitukashtam.mycommunity.controller;

import com.raitukashtam.mycommunity.request.CommunityMemberRequest;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityMemberResponse;
import com.raitukashtam.mycommunity.response.CommunityResponse;
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

    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(@RequestBody @Validated CommunityRequest request,
                                                               @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside createCommunity method of CommunityController");
        return new ResponseEntity<>(communityService.createCommunity(request, jwt.getSubject()), HttpStatus.CREATED);
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

    @DeleteMapping("/{communityId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable("communityId") Long communityId,
                                              @PathVariable("memberId") Long memberId,
                                              @AuthenticationPrincipal Jwt jwt) {
        log.info("Inside removeMember method of CommunityController");
        communityService.removeMember(communityId, memberId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
