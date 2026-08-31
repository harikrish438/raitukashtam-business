package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Amenity;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AmenityRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AmenityRequest;
import com.raitukashtam.mycommunity.response.AmenityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Amenity master data -- ADMIN manages, any ACTIVE member browses.
 * Membership authorization is delegated to CommunityService, same
 * pattern as every other phase.
 */
@Service
@Slf4j
public class AmenityService {
    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public AmenityResponse createAmenity(Long communityId, AmenityRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        if (request.isPaid() && request.getFee() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fee is required for a paid amenity");
        }

        Community community = communityRepository.getReferenceById(communityId);
        Amenity amenity = new Amenity();
        amenity.setCommunity(community);
        amenity.setName(request.getName().trim());
        amenity.setDescription(request.getDescription());
        amenity.setPaid(request.isPaid());
        amenity.setFee(request.isPaid() ? request.getFee() : null);
        amenity.setRules(request.getRules());
        amenity.setActive(true);
        Amenity saved = amenityRepository.save(amenity);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AmenityResponse> listAmenities(Long communityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return amenityRepository.findByCommunity_IdOrderByNameAsc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AmenityResponse getAmenity(Long communityId, Long amenityId, String callerIdentityId) {
        communityService.requireActiveMember(communityId, callerIdentityId);
        return toResponse(requireAmenity(communityId, amenityId));
    }

    @Transactional
    public AmenityResponse deactivateAmenity(Long communityId, Long amenityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Amenity amenity = requireAmenity(communityId, amenityId);
        if (!amenity.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Amenity is already inactive");
        }
        amenity.setActive(false);
        return toResponse(amenityRepository.save(amenity));
    }

    /** Package-private -- reused by AmenityBookingService so booking doesn't duplicate the "amenity exists in this community" lookup. */
    Amenity requireAmenity(Long communityId, Long amenityId) {
        return amenityRepository.findByIdAndCommunity_Id(amenityId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found with id: " + amenityId));
    }

    AmenityResponse toResponse(Amenity amenity) {
        return new AmenityResponse(
                amenity.getId(),
                amenity.getCommunity().getId(),
                amenity.getName(),
                amenity.getDescription(),
                amenity.isPaid(),
                amenity.getFee(),
                amenity.getRules(),
                amenity.isActive());
    }
}
