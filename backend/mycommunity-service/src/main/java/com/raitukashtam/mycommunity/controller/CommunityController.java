package com.raitukashtam.mycommunity.controller;

import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.service.CommunityService;
import com.raitukashtam.mycommunity.vo.ResponseTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/communities")
@Slf4j
public class CommunityController {
    @Autowired
    private CommunityService communityService;

    @PostMapping("/")
    public CommunityResponse saveCommunity(@RequestBody @Validated CommunityRequest request) {
        log.info("Inside save method of CommunityController");
        return communityService.save(request);
    }

    @GetMapping("/{communityId}")
    public ResponseTemplateVO getCommunityWithUser(@PathVariable("communityId") Long communityId, HttpServletRequest request) {
        log.info("Inside getCommunityWithUser method of CommunityController");
        return communityService.getCommunityWithUser(communityId, request);
    }
}
