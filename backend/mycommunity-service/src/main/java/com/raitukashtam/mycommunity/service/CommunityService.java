package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.CommunityRequest;
import com.raitukashtam.mycommunity.response.CommunityResponse;
import com.raitukashtam.mycommunity.vo.ResponseTemplateVO;
import com.raitukashtam.mycommunity.vo.User;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

@Service
@Slf4j
public class CommunityService {
    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${auth.service.url}")
    private String authServiceUrl;

    @Transactional
    public CommunityResponse save(CommunityRequest request) {
        log.info("Inside save method of CommunityService");
        try {
            // Check if community with the same name already exists (case-insensitive)
            if (communityRepository.existsByNameIgnoreCase(request.getName())) {
                throw new ResourceAlreadyExistsException("A community with the name '" + request.getName() + "' already exists");
            }
            Community community = new Community();
            community.setDescription(request.getDescription());
            community.setPrice(request.getPrice());
            community.setName(request.getName());
            community.setUserId(request.getUserId());
            Community communitySaved = communityRepository.save(community);
            return modelMapper.map(communitySaved, CommunityResponse.class);
        } catch (Exception e) {
            log.error("Error saving community: {}", e.getMessage(), e);
            throw e;
        }
    }

    public ResponseTemplateVO getCommunityWithUser(Long communityId, HttpServletRequest request) {
        log.info("Inside getCommunityWithUser method of CommunityService class");

        // Find community or throw exception if not found
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));

        // Extract Authorization header from incoming request
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            throw new RuntimeException("Authorization header is missing");
        }

        // Set up headers with Authorization from request
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorizationHeader);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Make the request with headers using configurable auth service URL
        String authUrl = authServiceUrl + "/users/" + community.getUserId();
        log.info("Calling auth service at: {}", authUrl);
        ResponseEntity<User> response = restTemplate.exchange(
            authUrl,
            HttpMethod.GET,
            entity,
            User.class
        );

        if (response.getBody() == null) {
            throw new RuntimeException("User not found for id: " + community.getUserId());
        }

        // Create and return response
        ResponseTemplateVO responseTemplateVO = new ResponseTemplateVO();
        responseTemplateVO.setCommunity(community);
        responseTemplateVO.setUser(response.getBody());
        return responseTemplateVO;
    }
}
